// Todos os plugins são declarados aqui (sem aplicar), para o classpath do
// build ser o mesmo em todos os módulos. Versões: gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
