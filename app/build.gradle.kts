plugins { id("com.android.application") }

android {
    namespace = "io.github.baiducarsilent"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.baiducarsilent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            // Keep hook entry names and runtime annotations intact.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // The real API 100 source is vendored, pinned and compile-only.
    // LSPosed supplies these classes at runtime; do not change to implementation.
    compileOnly(project(":libxposed-api100"))
}
