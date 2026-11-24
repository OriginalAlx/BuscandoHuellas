// ============================================================================
// Supabase Edge Function: generate-alerts
// ============================================================================
// Se dispara con un Database Webhook en INSERT sobre `public.reports`.
//
// Flujo:
//   1. Lee el reporte insertado (payload.record).
//   2. Carga los tokens FCM con notificaciones activas y sus preferencias.
//   3. Descarta al dueño del reporte, estados desactivados y usuarios fuera
//      del radio configurado.
//   4. Inserta una fila en `public.alerts` por cada destinatario.
//   5. Envía un push DATA-ONLY por FCM HTTP v1 (para que Android use el
//      BuscandoHuellasFirebaseMessagingService y su PendingIntent).
//   6. Elimina de la tabla los tokens que FCM reporta como inválidos.
//
// Variables de entorno (Project Settings → Edge Functions → Secrets):
//   SUPABASE_URL                  (la inyecta Supabase automáticamente)
//   SUPABASE_SERVICE_ROLE_KEY     (la inyecta Supabase automáticamente)
//   FCM_PROJECT_ID                ID del proyecto Firebase.
//   FCM_SERVICE_ACCOUNT_JSON      JSON completo de la cuenta de servicio.
//   WEBHOOK_SECRET                (opcional) token compartido con el webhook.
//
// Deploy:
//   supabase functions deploy generate-alerts --no-verify-jwt
//   supabase secrets set FCM_PROJECT_ID=... FCM_SERVICE_ACCOUNT_JSON="$(cat service-account.json)"
//
// Nota: `--no-verify-jwt` permite que el webhook invoque la función con el
// secreto propio. Si prefieres JWT, quita esa bandera y configura el webhook
// con el service_role key en el header Authorization.
// ============================================================================

import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY =
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
  Deno.env.get("SB_SERVICE_ROLE_KEY") ??
  "";
const FCM_PROJECT_ID = Deno.env.get("FCM_PROJECT_ID") ?? "";
const SERVICE_ACCOUNT_JSON =
  Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ??
  Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON") ??
  "";
const WEBHOOK_SECRET = Deno.env.get("WEBHOOK_SECRET") ?? "";

const ALERTS_TABLE = "alerts";
const TOKENS_TABLE = "user_notification_tokens";
const REPORTS_VIEW = "reports_view";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface ReportRecord {
  id: string;
  user_id: string;
  pet_name?: string | null;
  description?: string | null;
  status?: string | null;
  latitude?: number | string | null;
  longitude?: number | string | null;
  /** WKT devuelto por `reports_view` (`POINT(lon lat)`). */
  location_text?: string | null;
}

interface TokenRow {
  user_id: string;
  fcm_token: string;
  notifications_enabled: boolean;
  radius_km: number | null;
  notify_lost: boolean;
  notify_sighted: boolean;
  notify_found: boolean;
  latitude: number | null;
  longitude: number | null;
}

interface Recipient {
  user_id: string;
  fcm_token: string;
  distance_km: number | null;
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

interface WebhookPayload {
  type?: string;
  table?: string;
  record?: ReportRecord;
  old_record?: ReportRecord;
}

const STATUS_LABEL: Record<string, string> = {
  LOST: "Mascota perdida",
  SIGHTED: "Mascota avistada",
  FOUND: "Mascota encontrada",
};

const STATUS_PREFERENCE: Record<string, keyof TokenRow> = {
  LOST: "notify_lost",
  SIGHTED: "notify_sighted",
  FOUND: "notify_found",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

/** Distancia Haversine en kilómetros. */
function distanceKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const earthRadiusKm = 6371;
  const toRad = (value: number) => (value * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(km: number): string {
  return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1)} km`;
}

function truncate(text: string, max = 180): string {
  const clean = text.replace(/\s+/g, " ").trim();
  return clean.length <= max ? clean : `${clean.slice(0, max - 1).trimEnd()}…`;
}

function toNumber(value: unknown): number | null {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

/**
 * Obtiene las coordenadas del reporte.
 *
 * La tabla `reports` guarda una columna PostGIS `location`, pero la app lee la
 * vista `reports_view`, que expone `location_text` en WKT (`POINT(lon lat)`).
 * También se aceptan `latitude`/`longitude` por si el esquema cambia o para
 * pruebas manuales.
 */
function parseCoordinates(
  report: ReportRecord,
): { lat: number; lon: number } | null {
  const lat = toNumber(report.latitude);
  const lon = toNumber(report.longitude);
  if (lat != null && lon != null) return { lat, lon };

  const wkt = report.location_text?.trim();
  if (wkt) {
    const match = wkt.match(
      /POINT\s*\(\s*([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)\s+([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)\s*\)/i,
    );
    if (match) {
      const lonWkt = Number(match[1]);
      const latWkt = Number(match[2]);
      if (Number.isFinite(lonWkt) && Number.isFinite(latWkt)) {
        return { lat: latWkt, lon: lonWkt };
      }
    }
  }

  return null;
}

function buildTitle(status: string): string {
  return `${STATUS_LABEL[status] ?? "Nuevo reporte"} cerca de ti`;
}

function buildMessage(report: ReportRecord, km: number | null): string {
  const petName = report.pet_name?.trim() || "Una mascota";
  const description = report.description?.trim() ||
    "Toca para ver los detalles del reporte.";
  const where = km != null ? ` a ${formatDistance(km)}` : "";
  return truncate(`${petName}${where}. ${description}`);
}

async function getFcmAccessToken(): Promise<string> {
  if (!SERVICE_ACCOUNT_JSON) {
    throw new Error("Falta FCM_SERVICE_ACCOUNT_JSON en los secrets");
  }
  const account = JSON.parse(SERVICE_ACCOUNT_JSON) as ServiceAccount;
  const now = Math.floor(Date.now() / 1000);
  const key = await importPKCS8(account.private_key, "RS256");
  const assertion = await new SignJWT({
    scope: "https://www.googleapis.com/auth/firebase.messaging",
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(account.client_email)
    .setAudience(account.token_uri ?? "https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);

  const response = await fetch(
    account.token_uri ?? "https://oauth2.googleapis.com/token",
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(`OAuth FCM falló (${response.status}): ${await response.text()}`);
  }

  const data = await response.json() as { access_token?: string };
  if (!data.access_token) throw new Error("OAuth FCM no devolvió access_token");
  return data.access_token;
}

/**
 * Envía un mensaje DATA-ONLY. No se incluye el bloque `notification` a
 * propósito: así Android siempre llama a onMessageReceived y la app construye
 * la notificación con el PendingIntent y el canal "Reportes cercanos".
 */
async function sendFcm(
  accessToken: string,
  token: string,
  report: ReportRecord,
  title: string,
  message: string,
): Promise<{ ok: boolean; invalidToken: boolean }> {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          data: {
            report_id: report.id,
            title,
            message,
            body: message,
            status: report.status ?? "LOST",
          },
          android: {
            priority: "high",
            ttl: "3600s",
          },
        },
      }),
    },
  );

  if (response.ok) return { ok: true, invalidToken: false };

  const body = await response.text();
  const invalid = response.status === 404 ||
    body.includes("UNREGISTERED") ||
    body.includes("INVALID_ARGUMENT") ||
    body.includes("SENDER_ID_MISMATCH");

  console.error(`FCM error (${response.status}) para ${token}: ${body}`);
  return { ok: false, invalidToken: invalid };
}

async function fetchReport(
  supabase: ReturnType<typeof createClient>,
  reportId: string,
): Promise<ReportRecord | null> {
  // Se consulta la vista porque `reports.location` es una geometría PostGIS y
  // `reports_view` ya la expone como texto WKT legible.
  const { data, error } = await supabase
    .from(REPORTS_VIEW)
    .select(
      "id, user_id, pet_name, description, status, location_text",
    )
    .eq("id", reportId)
    .maybeSingle();

  if (error) {
    console.error("No se pudo leer el reporte", error);
    return null;
  }
  return (data as ReportRecord | null) ?? null;
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }
  if (req.method !== "POST") {
    return json({ error: "Método no permitido" }, 405);
  }

  if (WEBHOOK_SECRET) {
    const auth = req.headers.get("Authorization") ?? "";
    if (auth !== `Bearer ${WEBHOOK_SECRET}`) {
      return json({ error: "No autorizado" }, 401);
    }
  }

  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
    return json({ error: "Faltan SUPABASE_URL o SERVICE_ROLE_KEY" }, 500);
  }

  let payload: WebhookPayload;
  try {
    payload = await req.json();
  } catch {
    return json({ error: "JSON inválido" }, 400);
  }

  // Webhook de Supabase: { type, table, record }. También se acepta el reporte
  // directo para pruebas manuales.
  const record = payload.record ?? (payload as unknown as ReportRecord);
  const reportId = record?.id;

  if (!reportId) {
    return json({ error: "El payload no contiene un reporte válido" }, 400);
  }

  const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });

  // El webhook puede no traer todas las columnas; se completa desde la BD.
  let report = record.user_id ? record : await fetchReport(supabase, reportId);
  if (!report) {
    return json({ error: "Reporte no encontrado" }, 404);
  }

  // El payload del webhook sobre `reports` no incluye `location_text` (la
  // geometría PostGIS llega como WKB), así que sin coordenadas legibles se
  // relee el reporte desde `reports_view`.
  if (!parseCoordinates(report) || !report.status) {
    const fullReport = await fetchReport(supabase, reportId);
    if (fullReport) report = fullReport;
  }

  const status = (report.status ?? "LOST").toUpperCase();
  const preferenceColumn = STATUS_PREFERENCE[status];
  if (!preferenceColumn) {
    return json({ skipped: true, reason: `Estado desconocido: ${status}` });
  }

  const { data: tokenRows, error: tokensError } = await supabase
    .from(TOKENS_TABLE)
    .select(
      "user_id, fcm_token, notifications_enabled, radius_km, notify_lost, notify_sighted, notify_found, latitude, longitude",
    )
    .eq("notifications_enabled", true);

  if (tokensError) {
    console.error("No se pudieron leer los tokens", tokensError);
    return json({ error: "No se pudieron leer los tokens" }, 500);
  }

  const tokens = (tokenRows ?? []) as TokenRow[];
  const coordinates = parseCoordinates(report);
  const reportLat = coordinates?.lat ?? null;
  const reportLon = coordinates?.lon ?? null;

  const recipients: Recipient[] = [];

  for (const row of tokens) {
    if (!row.fcm_token) continue;
    if (row.user_id === report.user_id) continue; // no avisar al autor
    if (row[preferenceColumn] === false) continue;

    const hasReportLocation = reportLat != null && reportLon != null;
    const hasUserLocation =
      typeof row.latitude === "number" && typeof row.longitude === "number";

    let km: number | null = null;
    if (hasReportLocation && hasUserLocation) {
      km = distanceKm(
        row.latitude as number,
        row.longitude as number,
        reportLat as number,
        reportLon as number,
      );
    }

    if (row.radius_km != null) {
      // Con radio definido no se puede decidir sin ubicación del usuario.
      if (km == null || km > row.radius_km) continue;
    }

    recipients.push({
      user_id: row.user_id,
      fcm_token: row.fcm_token,
      distance_km: km,
    });
  }

  if (recipients.length === 0) {
    return json({ inserted: 0, sent: 0, reason: "Sin destinatarios" });
  }

  const title = buildTitle(status);

  // 1) Persistir alertas. El índice único (user_id, report_id) evita
  //    duplicados si el webhook se reintenta.
  const alertRows = recipients.map((recipient) => ({
    user_id: recipient.user_id,
    report_id: report.id,
    title,
    message: buildMessage(report, recipient.distance_km),
  }));

  const { error: insertError } = await supabase
    .from(ALERTS_TABLE)
    .upsert(alertRows, {
      onConflict: "user_id,report_id",
      ignoreDuplicates: true,
    });

  if (insertError) {
    console.error("No se pudieron insertar las alertas", insertError);
    return json({ error: "No se pudieron insertar las alertas" }, 500);
  }

  // 2) Push FCM. Se obtiene el access token una sola vez.
  let accessToken: string;
  try {
    accessToken = await getFcmAccessToken();
  } catch (error) {
    console.error("No se pudo autenticar con FCM", error);
    // Las alertas ya quedaron guardadas; se devuelve 200 para no provocar
    // reintentos duplicados del webhook.
    return json({
      inserted: alertRows.length,
      sent: 0,
      error: "FCM no configurado; alertas persistidas",
    });
  }

  let sent = 0;
  const invalidTokens: string[] = [];

  await Promise.all(
    recipients.map(async (recipient) => {
      const result = await sendFcm(
        accessToken,
        recipient.fcm_token,
        report,
        title,
        buildMessage(report, recipient.distance_km),
      );
      if (result.ok) sent += 1;
      if (result.invalidToken) invalidTokens.push(recipient.fcm_token);
    }),
  );

  // 3) Limpieza de tokens muertos.
  if (invalidTokens.length > 0) {
    const { error: deleteError } = await supabase
      .from(TOKENS_TABLE)
      .delete()
      .in("fcm_token", invalidTokens);

    if (deleteError) {
      console.error("No se pudieron borrar los tokens inválidos", deleteError);
    }
  }

  return json({
    report_id: report.id,
    inserted: alertRows.length,
    sent,
    invalid_tokens: invalidTokens.length,
  });
});
