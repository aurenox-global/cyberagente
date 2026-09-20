plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cyberagent.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cyberagent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "2.5.1"

        resourceConfigurations += listOf("es", "en")
    }

    signingConfigs {
        create("release") {
            val f = File(rootProject.projectDir, project.findProperty("cyberagent.storeFile") as String)
            storeFile = f
            storePassword = (System.getenv("CYBERAGENT_STORE_PASSWORD")
                ?: project.findProperty("cyberagent.storePassword")) as String?
            keyAlias = (System.getenv("CYBERAGENT_KEY_ALIAS")
                ?: project.findProperty("cyberagent.keyAlias")) as String?
            keyPassword = (System.getenv("CYBERAGENT_KEY_PASSWORD")
                ?: project.findProperty("cyberagent.keyPassword")) as String?
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
