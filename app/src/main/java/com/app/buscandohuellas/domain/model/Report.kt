package com.app.buscandohuellas.domain.model

/**
 * Modelo de dominio de un reporte de mascota perdida.
 *
 * Mapea la tabla `reports` de Supabase:
 * id, user_id, pet_name, description, status, image_url,
 * location (GEOGRAPHY(POINT,4326)) y created_at.
 *
 * En el dominio la geolocalización se expone como coordenadas separadas
 * (latitude/longitude) para no filtrar el detalle de PostGIS a las capas
 * superiores. La conversión a WKT ("POINT(lon lat)") se hace en la capa data.
 *
 * @param id generado por la base de datos (UUID); null antes de insertar.
 * @param userId propietario del reporte (auth.uid()).
 * @param status por defecto "LOST" para un reporte de mascota perdida.
 * @param latitude/longitude pueden ser null si aún no se ha obtenido la ubicación.
 */
data class Report(
    val id: String? = null,
    val userId: String,
    val petName: String,
    val description: String,
    val status: String = "LOST",
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
