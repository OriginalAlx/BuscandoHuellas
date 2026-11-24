-- Coincidencias entre reportes FOUND y LOST para el flujo "Encontré una mascota".
-- Ejecutar en el SQL Editor de Supabase.

CREATE TABLE IF NOT EXISTS report_matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    found_report_id UUID REFERENCES reports(id) ON DELETE CASCADE,
    lost_report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    matched_by UUID NOT NULL REFERENCES auth.users(id),
    confirmed_by_owner BOOLEAN DEFAULT false,
    similarity_score FLOAT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_matches_lost ON report_matches(lost_report_id);
CREATE INDEX IF NOT EXISTS idx_matches_found ON report_matches(found_report_id);

ALTER TABLE report_matches ENABLE ROW LEVEL SECURITY;

CREATE POLICY "matches_select" ON report_matches
    FOR SELECT TO authenticated
    USING (true);

CREATE POLICY "matches_insert" ON report_matches
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = matched_by);

CREATE POLICY "matches_update" ON report_matches
    FOR UPDATE TO authenticated
    USING (
        auth.uid() = matched_by
        OR EXISTS (
            SELECT 1 FROM reports
            WHERE id = report_matches.lost_report_id
              AND user_id = auth.uid()
        )
    );
