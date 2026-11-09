plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google's public TEST ids. Real ids are injected from the environment for release builds
// (see docs/SETUP.md) so no private id is ever committed.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testAppOpenId = "ca-app-pub-3940256099942544/9257395921"

val releaseAdmobAppId = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() } ?: testAdmobAppId
val releaseAppOpenId = System.getenv("ADMOB_APP_OPEN_ID")?.takeIf { it.isNotBlank() } ?: testAppOpenId
val releaseBannerId = System.getenv("ADMOB_BANNER_ID")?.takeIf { it.isNotBlank() } ?: testBannerId

val repoSlug = System.getenv("GITHUB_REPOSITORY") // "owner/repo" inside GitHub Actions
val privacyUrl = repoSlug?.split("/")?.takeIf { it.size == 2 }
    ?.let { "https://${it[0]}.github.io/${it[1]}/privacy-policy.html" }
    ?: "https://YOUR_GITHUB_USERNAME.github.io/CookingHandbook/privacy-policy.html"

android {
    namespace = "com.fws.cookinghandbook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fws.cookinghandbook"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 100
        versionName = "1.0.0"
        manifestPlaceholders["admobAppId"] = testAdmobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"$testAppOpenId\"")
        buildConfigField("String", "PRIVACY_URL", "\"$privacyUrl\"")
    }

    val ksPath = System.getenv("KEYSTORE_FILE")
    val hasKeystore = ksPath != null && file(ksPath).exists()
    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = releaseAdmobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$releaseBannerId\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"$releaseAppOpenId\"")
            // phones only: x86 images are for emulators and would add several MB of native code
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            // Without a real keystore the APK is signed with the debug key (testing only, not for Play).
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    bundle { language { enableSplit = false } } // keep every UI language inside the bundle
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.google.android.play:app-update:2.1.0")          // In-App Update (flexible)
    implementation("com.google.android.gms:play-services-ads:23.6.0")   // AdMob
    implementation("com.google.android.ump:user-messaging-platform:3.1.0") // consent (GDPR / US states)
    implementation("com.google.mlkit:translate:17.0.3")                 // on-device translation
}
