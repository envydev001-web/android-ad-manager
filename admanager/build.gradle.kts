plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.admanager"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // ✅ AndroidX core libs compatible with SDK 35
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // ✅ Ads + shimmer
    api("com.google.android.gms:play-services-ads:24.6.0") // stable for SDK 35
    api("com.facebook.shimmer:shimmer:0.5.0")

    // ✅ Scalable dp/sp units
    implementation("com.intuit.sdp:sdp-android:1.0.6")

    // ✅ Testing (latest compatible with SDK 35)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    api("com.google.firebase:firebase-database:21.0.0")
    implementation("com.google.firebase:firebase-config-ktx:21.3.0")

    implementation("com.google.code.gson:gson:2.10.1")
}

