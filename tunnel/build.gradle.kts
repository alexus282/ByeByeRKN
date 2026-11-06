// Java part of amneziawg-android's tunnel module (Apache-2.0, see LICENSE).
// libwg-go.so is taken prebuilt from the official AmneziaWG v3.1.20260814 release APK,
// so the build needs neither the NDK nor a Go toolchain.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.amnezia.awg.tunnel"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "LongLogTag"
        disable += "NewApi"
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    compileOnly(libs.jsr305)
}
