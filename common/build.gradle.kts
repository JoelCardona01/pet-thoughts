plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Paper ships SnakeYAML; the Fabric mod bundles it. Either way it is there at runtime.
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Java 21 bytecode: the Paper plugin must still load on 1.21.4 servers.
    options.release.set(21)
}
