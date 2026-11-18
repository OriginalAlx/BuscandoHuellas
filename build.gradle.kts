// Plugins declarados una sola vez y con `apply false`: solo fijan la versión
// en el classpath del build. Cada módulo los aplica en su propio bloque
// `plugins {}`. No repetir `id(...)` aquí o Gradle falla con
// "Plugin with id '...' was already requested at line N".
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
}
