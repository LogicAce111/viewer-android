import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val viewerSigningFile = providers.gradleProperty("viewer.signing.properties").orNull
val viewerSigning = Properties().apply {
    viewerSigningFile?.let { path ->
        rootProject.file(path).inputStream().use(::load)
    }
}

android {
    namespace = "com.legion.viewer"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.legion.viewer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (viewerSigningFile != null) {
            create("release") {
                storeFile = file(viewerSigning.getProperty("storeFile") ?: error("签名配置缺少 storeFile"))
                storePassword = viewerSigning.getProperty("storePassword") ?: error("签名配置缺少 storePassword")
                keyAlias = viewerSigning.getProperty("keyAlias") ?: error("签名配置缺少 keyAlias")
                keyPassword = viewerSigning.getProperty("keyPassword") ?: error("签名配置缺少 keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
}

dependencies {
    // Keep Compose libraries aligned while compiling and targeting Android API 37.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.media:media:1.7.1")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.videolan.android:libvlc-all:3.7.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
