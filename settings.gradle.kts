pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "pet-thoughts"

// common:   platform-independent brain (triggers, anti-spam, config, language files).
// paper:    the server plugin  -> paper/build/libs/PetThoughts-<version>.jar
// fabric:   the Fabric mod     -> fabric/build/libs/pet-thoughts-fabric-<version>.jar
// neoforge: the NeoForge mod   -> neoforge/build/libs/pet-thoughts-neoforge-<version>.jar
// forge:    the Forge mod      -> forge/build/libs/pet-thoughts-forge-<version>.jar
// shared/ is not a project: it holds the sources that the NeoForge and Forge mods have in common.
// Backport branch for Minecraft 26.1.2: only the Fabric and NeoForge mods are built here.
// The Paper plugin (one jar for every version) and the Forge mod are built from main.
include("common", "fabric", "neoforge")
