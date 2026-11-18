package com.app.buscandohuellas.domain.repository

/**
 * Operaciones destructivas sobre la cuenta del usuario autenticado.
 *
 * Se mantiene separado de [ProfileRepository] porque la eliminación de cuenta
 * afecta a varias tablas y al almacenamiento local, no solo al perfil.
 */
interface AccountRepository {

    /**
     * Elimina los datos del usuario (reportes, perfil y token FCM), limpia las
     * preferencias locales y cierra la sesión.
     *
     * Devuelve [Result] para que la UI pueda mostrar un error claro sin tener
     * que manejar excepciones de red o de Supabase.
     */
    suspend fun deleteAccount(): Result<Unit>
}
