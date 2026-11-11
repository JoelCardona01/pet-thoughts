plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("PetThoughts")
    // The common module is copied into the plugin jar: one file to drop into /plugins.
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
}

evaluationDependsOn(":common")

