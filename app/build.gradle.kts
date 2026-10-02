plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.botik.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.botik.keyboard"
        // Poco M5 ships with Android 12 (MIUI 13) and updates to Android 13/14.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // CI passes a stable keystore through env vars so updates install over the old APK.
        // Without it the release build falls back to the debug key.
        val storePath = System.getenv("BOTIK_KEYSTORE_PATH")
        if (!storePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = System.getenv("BOTIK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BOTIK_KEY_ALIAS")
                keyPassword = System.getenv("BOTIK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "META-INF/{AL2.0,LGPL2.1}"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Claude API (native-quality translation)
    implementation("com.anthropic:anthropic-java:2.68.0")

    // On-device draft translation (instant, offline)
    implementation("com.google.mlkit:translate:17.0.3")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
}
