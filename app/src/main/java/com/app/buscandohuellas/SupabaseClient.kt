package com.app.buscandohuellas
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

// Código de Deep Links eliminado - confirmación de email desactivada en Supabase
object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://jebvukcmluaaarjumyws.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImplYnZ1a2NtbHVhYWFyanVteXdzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0NzMwNjQsImV4cCI6MjEwNDA0OTA2NH0.YQmJjj3Fdnrhd3OKCOMfDG06LA2r5oUAeaq6D9eUQE4"
    ) {
        install(Postgrest) // Para base de datos
        install(Auth)      // Para autenticación (Login)
        install(Storage)   // Para subir fotos
        install(Realtime)  // Para alertas en vivo
    }
}