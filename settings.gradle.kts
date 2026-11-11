pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "pet-thoughts"

// common: platform-independent brain (triggers, anti-spam, config, language files).
// paper:  the server plugin  -> paper/build/libs/PetThoughts-<version>.jar
// fabric: the Fabric mod     -> fabric/build/libs/pet-thoughts-fabric-<version>.jar
include("common", "paper", "fabric")
