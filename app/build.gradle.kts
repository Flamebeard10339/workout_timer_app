plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.flamebeard10339.circuitclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.flamebeard10339.circuitclock"
        minSdk = 26          // adaptive icons; also well clear of the old addJavascriptInterface flaw
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Off by default: the 205 KB HTML asset dwarfs the ~150 lines of Kotlin,
            // so shrinking buys nothing and only adds a way for the build to surprise you.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = false }

    // F-Droid builds should be reproducible: leave out the signed dependency-metadata blob
    // that Gradle otherwise embeds, since it is not derivable from source.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

dependencies {
    // The only dependency. WebViewAssetLoader serves assets over a real https origin so
    // localStorage is durable and the page counts as a secure context.
    implementation("androidx.webkit:webkit:1.11.0")
}
