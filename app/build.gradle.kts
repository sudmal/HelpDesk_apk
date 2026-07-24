import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Read version.properties at configuration time
val versionProps = Properties().also { props ->
    rootProject.file("version.properties").inputStream().use { props.load(it) }
}
val verMajor   = versionProps.getProperty("VERSION_MAJOR", "1").trim()
val verMinor   = versionProps.getProperty("VERSION_MINOR", "0").trim()
val verPatch   = versionProps.getProperty("VERSION_PATCH", "0").trim()
val buildNum   = versionProps.getProperty("BUILD_NUMBER", "1").trim().toInt()

// Increment BUILD_NUMBER after each successful assembly (debug or release)
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    doLast {
        versionProps["BUILD_NUMBER"] = (buildNum + 1).toString()
        rootProject.file("version.properties").writer().use { versionProps.store(it, null) }
        println("Build $buildNum complete. Next build will be ${buildNum + 1}.")
    }
}

android {
    namespace = "com.sputnik.helpdesk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sputnik.helpdesk"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNum
        versionName = "$verMajor.$verMinor.$verPatch"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true; buildConfig = true }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "SP-Helpdesk-${versionName}.${versionCode}-${buildType.name}.apk"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Lifecycle + Coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Image loading
    implementation("io.coil-kt:coil:2.6.0")
}
