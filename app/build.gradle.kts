// Public deployment settings only. Never put Razorpay secrets in Gradle properties.
fun billingSetting(name: String, fallback: String = "") = "\"" + providers.gradleProperty(name).orElse(fallback).get()
    .replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.spendtracker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.spendtracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("boolean", "AUTOPAY_ENABLED", "false")
        buildConfigField("String", "BILLING_BASE_URL", billingSetting(
            "billingBaseUrl",
            "https://dwatnipgdvuftwcsgohm.supabase.co/functions/v1/billing-api"
        ))
        buildConfigField("String", "BILLING_GOOGLE_CLIENT_ID", billingSetting("billingGoogleClientId"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildTypes {
        create("autopay") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".autopay"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "AUTOPAY_ENABLED", "true")
        }
        create("autopayRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".autopay"
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "AUTOPAY_ENABLED", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.activity)
    
    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    
    // Hilt
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
    
    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    
    // Charts
    implementation(libs.mp.android.chart)

    // Excel Export (Android-compatible .xlsx writer, no JPMS/jlink deps)
    implementation(libs.fastexcel)

    // Security & Encryption
    implementation(libs.biometric)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)

    // Google Sign-In (for Drive backup)
    implementation(libs.google.play.services.auth)
    implementation(libs.google.play.billing)
    implementation("com.razorpay:checkout:1.6.41")
    // Vendor checkout uses dynamic core versions. Pin the verified pair for reproducible releases.
    constraints {
        implementation("com.razorpay:standard-core:1.7.19") { version { strictly("1.7.19") } }
        implementation("com.razorpay:core:1.0.19") { version { strictly("1.0.19") } }
    }
    implementation(libs.google.play.review)
    implementation(project(":prediction"))

    // PDF Bulk Ingestion
    implementation(libs.pdfbox.android)
    // On-device OCR for scanned bank statements. Bundled so statements never need to
    // leave the device and OCR is available on first use.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.androidx.core.testing)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.core.testing) {
        // The instrumentation tests use the executor rule, not its JVM Mockito dependency.
        exclude(group = "org.mockito", module = "mockito-core")
    }
}
