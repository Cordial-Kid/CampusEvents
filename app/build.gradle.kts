import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.campusevents"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.campusevents"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:contracts"))
    implementation(project(":core:platform"))
    implementation(project(":feature:event"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:registration"))
    implementation(project(":feature:engagement"))
    implementation(libs.fragment)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

val requiredFeatures = listOf(":feature:browse", ":feature:registration", ":feature:engagement")
requiredFeatures.forEach { evaluationDependsOn(it) }
val pendingFeatures = requiredFeatures
    .filter { !project(it).extra.has("integrationReady") || project(it).extra["integrationReady"] != true }

abstract class VerifyFeatureIntegration : DefaultTask() {
    @get:Input
    abstract val pendingModules: ListProperty<String>

    @TaskAction
    fun verify() {
        check(pendingModules.get().isEmpty()) {
            "Complete the required APIs, UI and ports before release: ${pendingModules.get().joinToString()}"
        }
    }
}

val verifyFeatureIntegration = tasks.register<VerifyFeatureIntegration>("verifyFeatureIntegration") {
    group = "verification"
    description = "Blocks release packaging while development feature adapters remain."
    pendingModules.set(pendingFeatures)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyFeatureIntegration)
}
