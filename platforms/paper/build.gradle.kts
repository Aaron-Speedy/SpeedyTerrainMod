plugins {
    `java-library`
    id("platform-conventions")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing
}

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems that only have JDK 11 installed for example.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    maven("https://maven.papermc.io/repo/")
    maven("https://maven.enginehub.org/repo/")
    mavenCentral()
}

// paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
// paperweight {
//     reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
// }

dependencies {
    paperweight.paperDevBundle("1.21.7-R0.1-SNAPSHOT")
    implementation(project(":common:common-core"))
    // TODO: Update when WorldEdit is available for 1.21.7
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.11") {
        exclude("org.yaml")
    }
    // compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.11")
}

tasks {
    processResources {
        val replacements = mapOf(
            "version" to project.version
        )
        inputs.properties(replacements)

        filesMatching("plugin.yml") {
            expand(replacements)
        }
    }
}

otgPlatform {
    productionJar.set(tasks.reobfJar.flatMap { it.outputJar })
}
