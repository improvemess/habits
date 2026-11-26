buildscript {
    repositories {
        google {
            content {
                includeGroup("com.android.tools")
            }
        }
    }
    dependencies {
        // Muss vor AGP auf dem Klassenpfad stehen, damit es dessen R8 ersetzt (#108).
        classpath(libs.r8)
    }
}

plugins {
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint.plugin) apply false
    alias(libs.plugins.shadow) apply false
}

apply {
    from("gradle/translators.gradle.kts")
}
