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
    runtimeOnly(libs.sqlite.jdbc)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit)
    testCompileOnly(libs.paper.api)
    testRuntimeOnly(libs.paper.api)
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
    val paperVersion = libs.versions.paper.get()
    val paperApiVersion = paperVersion.substringBefore(".build.")
    require(paperApiVersion != paperVersion) {
        "Paper version '$paperVersion' must use the '<api-version>.build.+' format"
    }
    inputs.property("version", pluginVersion)
    inputs.property("paperApiVersion", paperApiVersion)
    filesMatching("plugin.yml") {
        expand(
            "version" to pluginVersion,
            "paperApiVersion" to paperApiVersion,
        )
    }
}

tasks.jar {
    archiveBaseName = "MonsterHuntReloaded"
    destinationDirectory = layout.projectDirectory.dir("target")
}

tasks.clean {
    delete(layout.projectDirectory.dir("target"))
}
