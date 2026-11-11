plugins {
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
}

base {
    archivesName.set("pet-thoughts-fabric")
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    implementation(project(":common"))
    // Minecraft does not ship a YAML parser, so the mod carries one inside its jar.
    implementation("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    include("org.yaml:snakeyaml:${property("snakeyaml_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.jar {
    // The common module is copied into the mod jar: one file to drop into /mods.
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_pet-thoughts" }
    }
}

evaluationDependsOn(":common")
