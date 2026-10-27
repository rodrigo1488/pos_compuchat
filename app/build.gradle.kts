import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val atualizacaoProperties = Properties().apply {
    val arquivo = rootProject.file("atualizacao.properties")
    if (arquivo.exists()) {
        arquivo.inputStream().use { load(it) }
    }
}

fun atualizacao(nome: String, padrao: String): String {
    val ambiente = System.getenv(nome)?.trim().orEmpty()
    if (ambiente.isNotEmpty()) return ambiente
    return atualizacaoProperties.getProperty(nome)?.trim()?.takeIf { it.isNotEmpty() } ?: padrao
}

fun asBuildConfigString(valor: String): String =
    "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.compumais.poscompuchat"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.compumais.poscompuchat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "UTILITY_UPDATE_BASE",
            asBuildConfigString(atualizacao("COMPUTICKET_UPDATE_BASE", "https://www.computicket.space")),
        )
        buildConfigField(
            "String",
            "UTILITY_UPDATE_HASH",
            asBuildConfigString(atualizacao("COMPUTICKET_UPDATE_HASH", "")),
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}