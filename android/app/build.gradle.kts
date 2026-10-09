import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Versão do app. O CI injeta os dois valores (ORG_GRADLE_PROJECT_painelVersionCode
// = número da run, que só cresce; painelVersionName = rótulo legível). Sem eles,
// o build local fica com 1 / "0.0.0-dev". Detalhes: README, "App Android".
val appVersionCode = providers.gradleProperty("painelVersionCode").map(String::toInt).getOrElse(1)
val appVersionName = providers.gradleProperty("painelVersionName").getOrElse("0.0.0-dev")

// Assinatura do release: só no CI, com o keystore vindo dos secrets
// (ANDROID_KEYSTORE_*). Sem essas variáveis o release sai sem assinatura, que
// não instala — de propósito: APK oficial só sai do Actions.
val keystoreDoRelease = providers.environmentVariable("ANDROID_KEYSTORE_ARQUIVO")

android {
    namespace = "io.github.gfvdataweb.painelstatus"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Identidade do app no Android: depois de publicado NÃO muda.
        applicationId = "io.github.gfvdataweb.painelstatus"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreDoRelease.isPresent) {
            create("release") {
                storeFile = file(keystoreDoRelease.get())
                storeType = "pkcs12"
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_SENHA").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_SENHA").get()
            }
        }
    }

    buildTypes {
        debug {
            // O debug instala ao lado do app oficial (outro id, nome "(debug)").
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // Sem R8: o APK de release roda o mesmo código que os testes
            // exercitam no debug (minificação fica para quando o tamanho importar).
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric precisa dos recursos (strings, tema) nos testes JVM.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Lint barra o build em qualquer aviso. Ficam de fora só as checagens
        // que dependem da data (versão nova publicada etc.): o mesmo commit tem
        // que dar o mesmo resultado hoje e daqui a um ano.
        abortOnError = true
        warningsAsErrors = true
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
    }
}

kotlin {
    compilerOptions {
        // Aviso do compilador também quebra o build (ex.: API depreciada após atualizar dependência).
        allWarningsAsErrors = true
    }
}

tasks.withType<Test>().configureEach {
    // JDK 17+ fecha as classes internas do Java; o Robolectric precisa delas
    // (lista recomendada em robolectric.org/getting-started).
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    )
    // Os testes leem o que o painel publica de verdade (docs/: dados/status.json
    // e dados/historico/): se o formato mudar de um jeito que quebra o app, o CI acusa.
    val painelPublicado = rootProject.file("../docs")
    inputs.dir(painelPublicado.resolve("dados")).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("dadosDoPainel")
    systemProperty("painel.publicado", painelPublicado.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
}
