plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.crosspoint.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crosspoint.launcher"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "com.crosspoint.launcher.IntegrationChecks"
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 4
        versionName = rootProject.file("VERSION").readText().trim()
    }

    val releaseKeystore = System.getenv("CROSSPOINT_KEYSTORE")?.let(::file)
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("CROSSPOINT_KEYSTORE_PASSWORD")
            keyAlias = "crosspoint"
            keyPassword = System.getenv("CROSSPOINT_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        val variant = this
        outputs.configureEach {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.let { output ->
                output.outputFileName = "crosspoint-launcher-v${variant.versionName}-b${variant.versionCode}-${variant.buildType.name}.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.jmdns)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
}
