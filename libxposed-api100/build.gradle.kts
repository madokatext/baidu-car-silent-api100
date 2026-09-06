plugins { id("com.android.library") }

android {
    namespace = "io.github.libxposed.api"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures {
        androidResources = false
        buildConfig = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies { compileOnly("androidx.annotation:annotation:1.8.2") }
