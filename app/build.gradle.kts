import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/*
 * Release signing. Locally the values come from keystore.properties, which is
 * gitignored. CI passes the same values as environment variables instead, so no
 * key material ever enters the repository. When neither is available the release
 * build is left unsigned rather than failing, so assembleRelease still works on
 * a machine that holds no key.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingProperty(environment: String, property: String): String? =
    System.getenv(environment) ?: keystoreProperties.getProperty(property)

val releaseStoreFile = signingProperty("KEYSTORE_FILE", "storeFile")

android {
    namespace = "com.sdev.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sdev.launcher"
        minSdk = 33
        targetSdk = 37
        versionCode = 4
        versionName = "0.2.2"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingProperty("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingProperty("KEY_ALIAS", "keyAlias")
                keyPassword = signingProperty("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // The home screen's cell arithmetic is plain Kotlin, so it is checked with
    // plain JUnit rather than on a device.
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}
