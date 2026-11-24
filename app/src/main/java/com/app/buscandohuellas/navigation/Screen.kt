package com.app.buscandohuellas.navigation

import android.net.Uri

/**
 * Rutas centralizadas de la app.
 *
 * Se usan constantes para evitar errores tipográficos entre el NavHost y las
 * pantallas. Las rutas con argumentos exponen además una función auxiliar que
 * codifica el valor para que IDs con caracteres especiales no rompan la URL.
 */
object Screen {

    // Rutas raíz.
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Pestañas principales (cada una conserva su propio estado en el backstack).
    const val HOME = "home"
    const val MAP = "map"
    const val ALERTS = "alerts"
    const val PROFILE = "profile"

    // Subpantallas.
    const val MY_REPORTS = "my_reports"
    const val MY_REPORTS_ROUTE = "my_reports?userId={userId}"
    const val EDIT_PROFILE = "edit_profile/{userId}"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val SETTINGS = "settings"
    const val REPORT_DETAIL = "report_detail/{reportId}"
    const val COMMENTS = "comments/{reportId}"
    const val REPORT_TYPE_SELECTION = "report_type_selection"
    const val CREATE_REPORT = "create_report/{reportType}"
    const val FOUND_PET_MATCH = "found_pet_match/{reportId}"
    const val EDIT_REPORT = "edit_report/{reportId}"
    const val MODERATOR_DASHBOARD = "moderator_dashboard"
    const val CHAT_LIST = "chat_list"
    const val CHAT =
        "chat/{conversationId}?ownerId={ownerId}&ownerName={ownerName}&" +
            "ownerAvatarUrl={ownerAvatarUrl}&reportId={reportId}"

    /** Marcador usado cuando el chat se abre desde un reporte y aún no hay hilo. */
    const val NEW_CONVERSATION_ID = "new"

    // Nombres de argumentos compartidos por el NavHost.
    const val ARG_USER_ID = "userId"
    const val ARG_REPORT_ID = "reportId"
    const val ARG_REPORT_TYPE = "reportType"
    const val ARG_CONVERSATION_ID = "conversationId"
    const val ARG_OWNER_ID = "ownerId"
    const val ARG_OWNER_NAME = "ownerName"
    const val ARG_OWNER_AVATAR = "ownerAvatarUrl"

    // Deep link de notificaciones: buscandohuellas://report/{reportId}.
    const val DEEP_LINK_SCHEME = "buscandohuellas"
    const val DEEP_LINK_HOST = "report"
    const val REPORT_DEEP_LINK_PATTERN = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/{$ARG_REPORT_ID}"

    /** Rutas que muestran la barra de navegación inferior. */
    val MAIN_ROUTES: Set<String> = setOf(HOME, MAP, ALERTS, CHAT_LIST, PROFILE)

    /** Ruta de detalle para un reporte concreto. */
    fun reportDetail(reportId: String): String =
        "report_detail/${Uri.encode(reportId)}"

    /** Ruta de edición de perfil para un usuario concreto. */
    fun editProfile(userId: String): String =
        "edit_profile/${Uri.encode(userId)}"

    /** Ruta de edición de un reporte concreto. */
    fun editReport(reportId: String): String =
        "edit_report/${Uri.encode(reportId)}"

    /** Ruta de creación de reporte con tipo fijo (LOST o FOUND). */
    fun createReport(reportType: String): String =
        "create_report/${Uri.encode(reportType)}"

    /** Ruta de emparejamiento tras crear un reporte FOUND. */
    fun foundPetMatch(reportId: String): String =
        "found_pet_match/${Uri.encode(reportId)}"

    /** Ruta de los comentarios de un reporte concreto. */
    fun comments(reportId: String): String =
        "comments/${Uri.encode(reportId)}"

    /**
     * Ruta del chat con el dueño de un reporte.
     *
     * El nombre y la foto viajan como query params opcionales para que el
     * header se pinte de inmediato, sin esperar otra consulta. El reporte
     * identifica el hilo y el dueño se usa como respaldo si falta el nombre.
     */
    fun chat(
        ownerId: String,
        ownerName: String,
        ownerAvatarUrl: String? = null,
        reportId: String? = null
    ): String {
        val encodedName = Uri.encode(ownerName.ifBlank { "Dueño del reporte" })
        val encodedAvatar = Uri.encode(ownerAvatarUrl.orEmpty())
        val encodedReport = Uri.encode(reportId.orEmpty())

        return "chat/$NEW_CONVERSATION_ID" +
            "?$ARG_OWNER_ID=${Uri.encode(ownerId)}" +
            "&$ARG_OWNER_NAME=$encodedName" +
            "&$ARG_OWNER_AVATAR=$encodedAvatar" +
            "&$ARG_REPORT_ID=$encodedReport"
    }

    /** Ruta del chat ya existente al que se llega desde la lista de mensajes. */
    fun chatById(conversationId: String): String =
        "chat/${Uri.encode(conversationId)}"


    /**
     * Ruta de "Mis reportes". Si [userId] es nulo o vacío se navega a la ruta
     * base y el ViewModel resuelve el usuario desde la sesión.
     */
    fun myReports(userId: String? = null): String =
        if (userId.isNullOrBlank()) {
            MY_REPORTS
        } else {
            "$MY_REPORTS?$ARG_USER_ID=${Uri.encode(userId)}"
        }
}
