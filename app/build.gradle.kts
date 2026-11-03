plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.swill.maxchat"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.swill.maxchat"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}
configurations.configureEach {
    resolutionStrategy {
        force("androidx.fragment:fragment:1.8.0")
        force("androidx.fragment:fragment-ktx:1.8.0")
    }
}
