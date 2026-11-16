plugins {
    java
    id("net.minecraftforge.gradle") version "[7.0.29,8.0)"
    id("net.minecraftforge.jarjar") version "0.2.3"
}

base {
    archivesName.set("pet-thoughts-forge")
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

// The mod itself (everything that only needs vanilla classes) is in shared/ and is compiled
// into both the NeoForge and the Forge jar; this module adds the Forge entry point.
sourceSets.main {
    java.srcDir(rootProject.file("shared/src/main/java"))
    resources.srcDir(rootProject.file("shared/src/main/resources"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Jar-in-Jar: the "jarJar" task builds the jar people download (the plain "jar" plus the
// libraries from the jarJar configuration). It keeps the normal file name; the plain jar is
// written to build/slim so that build/libs only holds the file to publish.
jarJar.register() {
    archiveClassifier = null
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}"))

    implementation(project(":common"))
    // Minecraft does not ship a YAML parser, so the mod carries one inside its jar.
    implementation("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    "jarJar"("org.yaml:snakeyaml:${property("snakeyaml_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val forgeVersion = project.property("forge_version") as String
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "minecraft_next_version" to project.property("minecraft_next_version"),
        "forge_major" to forgeVersion.substringBefore('.'),
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("META-INF/mods.toml") {
        expand(props)
    }
    from(rootProject.file("docs/media/icon.png"))
}

tasks.jar {
    archiveClassifier = "slim"
    destinationDirectory = layout.buildDirectory.dir("slim")
    manifest {
        // On Forge, Mixin finds the mod's mixins through the jar manifest.
        attributes["MixinConfigs"] = "petthoughts.mixins.json"
    }
    // The common module is copied into the mod jar: one file to drop into /mods.
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_pet-thoughts" }
    }
}

evaluationDependsOn(":common")
