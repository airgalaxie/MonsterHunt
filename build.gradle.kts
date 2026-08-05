plugins {
    java
}

group = "de.airgalaxie"
version = "2.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "PaperMC"
    }
}

dependencies {
    compileOnly(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    archiveBaseName = "MonsterHuntReloaded"
}
