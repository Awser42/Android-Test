plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdMobBannerId = "ca-app-pub-3940256099942544/9214589741"
val adMobPublisher = "5696841849162209"
val releaseAdMobAppId = providers.gradleProperty("ADMOB_APP_ID")
    .orElse(providers.environmentVariable("ADMOB_APP_ID"))
    .orElse("ca-app-pub-5696841849162209~6162796310").get().trim()
val releaseAdMobBannerId = providers.gradleProperty("ADMOB_BANNER_ID")
    .orElse(providers.environmentVariable("ADMOB_BANNER_ID"))
    .orElse("ca-app-pub-5696841849162209/7022917306").get().trim()
require(releaseAdMobAppId.matches(Regex("ca-app-pub-$adMobPublisher~[0-9]{10}"))) {
    "ADMOB_APP_ID must be a valid AdMob app ID for publisher $adMobPublisher; app IDs use ~, not /."
}
require(releaseAdMobBannerId.matches(Regex("ca-app-pub-$adMobPublisher/[0-9]{10}"))) {
    "ADMOB_BANNER_ID must be a valid banner unit ID for publisher $adMobPublisher; banner IDs use /."
}

// Release credentials are supplied only by the build environment, never checked in.
val releaseSigningNames = listOf(
    "ANDROID_RELEASE_STORE_FILE", "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS", "ANDROID_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull }
val releaseSigningRequested = releaseSigningValues.values.any { it != null }
require(!releaseSigningRequested || releaseSigningValues.values.all { !it.isNullOrBlank() }) {
    "Release signing requires all four ANDROID_RELEASE_STORE_FILE, ANDROID_RELEASE_STORE_PASSWORD, ANDROID_RELEASE_KEY_ALIAS and ANDROID_RELEASE_KEY_PASSWORD environment variables."
}
val releaseStoreFile = if (releaseSigningRequested) {
    rootProject.file(releaseSigningValues.getValue("ANDROID_RELEASE_STORE_FILE")!!.trim()).also {
        require(it.isFile && it.canRead()) { "ANDROID_RELEASE_STORE_FILE must point to a readable keystore file." }
    }
} else null

android {
    namespace = "com.awser.counter"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    useLibrary("android.test.runner", false)
    useLibrary("android.test.base", false)
    defaultConfig {
        applicationId = "com.awser.counter"
        minSdk = 23
        targetSdk = 36
        versionCode = 11
        versionName = "1.8.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        manifestPlaceholders["adMobAppId"] = testAdMobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$testAdMobBannerId\"")
        buildConfigField("boolean", "ADMOB_TEST_ADS", "true")
    }
    buildFeatures { buildConfig = true }
    if (releaseSigningRequested) {
        signingConfigs.create("releaseFromEnvironment") {
            storeFile = releaseStoreFile
            storePassword = releaseSigningValues.getValue("ANDROID_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("ANDROID_RELEASE_KEY_ALIAS")!!.trim()
            keyPassword = releaseSigningValues.getValue("ANDROID_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            if (releaseSigningRequested) signingConfig = signingConfigs.getByName("releaseFromEnvironment")
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["adMobAppId"] = releaseAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$releaseAdMobBannerId\"")
            buildConfigField("boolean", "ADMOB_TEST_ADS", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            // Preview APKs are safe to interact with: they never request the owner's live unit.
            manifestPlaceholders["adMobAppId"] = testAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testAdMobBannerId\"")
            buildConfigField("boolean", "ADMOB_TEST_ADS", "true")
        }
        create("live") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            // Installable owner review build using the configured production ad units.
            // Keep this certificate for updates; the owner's Play release signing is separate.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { warningsAsErrors = false }
}
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    // Includes a local Ads implementation for Android/Fire devices without Play services.
    implementation("com.google.android.gms:play-services-ads:25.0.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    maxHeapSize = "2g"
}
