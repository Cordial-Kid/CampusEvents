plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.campusevents.event"
    compileSdk = 37
    resourcePrefix = "event_"
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:contracts"))
    implementation(project(":core:platform"))
    implementation(libs.appcompat)
    implementation(libs.fragment)
    implementation(libs.lifecycle.viewmodel)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
