plugins { alias(libs.plugins.android.library) }
// Set true only after the required APIs, transaction ports and UI are real.
extra["integrationReady"] = true
android {
 namespace = "com.example.campusevents.registration"
 compileSdk { version = release(37) }
 defaultConfig { minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 resourcePrefix = "reg_"
 compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
 implementation(project(":core:contracts"))
 implementation(project(":core:platform"))
 androidTestImplementation(libs.ext.junit)
 androidTestImplementation(libs.espresso.core)
 implementation(libs.appcompat)
 implementation(libs.fragment)
 implementation(libs.lifecycle.viewmodel)
 implementation(libs.lifecycle.livedata)
}
