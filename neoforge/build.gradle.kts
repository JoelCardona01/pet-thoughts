plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.148"
}

base {
    archivesName.set("pet-thoughts-neoforge")
}

repositories {
    mavenCentral()
}

// The mod itself (everything that only needs vanilla classes) is in shared/ and is compiled
// into both the NeoForge and the Forge jar; this module adds the NeoForge entry point.
sourceSets.main {
    java.srcDir(rootProject.file("shared/src/main/java"))
    resources.srcDir(rootProject.file("shared/src/main/resources"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

neoForge {
    version = property("neo_version") as String

    mods {
        create("petthoughts") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":common"))
    // Minecraft does not ship a YAML parser, so the mod carries one inside its jar (Jar-in-Jar).
    implementation("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    "jarJar"("org.yaml:snakeyaml") {
        version {
            strictly("[2.0,3.0)")
            prefer(property("snakeyaml_version") as String)
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "neo_min_version" to project.property("neo_min_version"),
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
    from(rootProject.file("docs/media/icon.png"))
}

tasks.jar {
    // The common module is copied into the mod jar: one file to drop into /mods.
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_pet-thoughts" }
    }
}

evaluationDependsOn(":common")

// Fails the build if a mixin targets something this Minecraft version does not have.
apply(from = rootProject.file("gradle/verify-mixins.gradle.kts"))
