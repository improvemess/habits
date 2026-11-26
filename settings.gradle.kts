pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

include(":uhabits-android", ":uhabits-core")
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Nur für AppIntro, das nicht auf Maven Central veröffentlicht wird. Auf die
        // Gruppe beschränkt, damit keine andere Abhängigkeit unbemerkt von jitpack kommt
        // (F-Droid, #109).
        maven(url = "https://jitpack.io") {
            content {
                includeGroup("com.github.AppIntro")
            }
        }
    }
}

include(":uhabits-android", ":uhabits-core")
