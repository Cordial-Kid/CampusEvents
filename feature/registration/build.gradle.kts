plugins { alias(libs.plugins.android.library) }
// Set true only after the required APIs, transaction ports and UI are real.
extra["integrationReady"] = false
android {
 namespace = "com.example.campusevents.registration"
 compileSdk { version = release(37) }
 defaultConfig { minSdk = 24 }
 resourcePrefix = "reg_"
 compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
 implementation(project(":core:contracts"))
 implementation(project(":core:platform"))
 implementation(libs.appcompat)
 implementation(libs.fragment)
 implementation(libs.lifecycle.viewmodel)
 implementation(libs.lifecycle.livedata)
}
