plugins {
    `java-library`
    id("platform-conventions")
    // id("io.papermc.paperweight.userdev") version "1.3.6"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.16"
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing
}

repositories {
    maven("https://maven.enginehub.org/repo/")
}

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 17 on systems that only have JDK 8 installed for example.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    //paperDevBundle("1.18.1-R0.1-SNAPSHOT")

    // paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:1.21.5-R0.1-SNAPSHOT")
    // compileOnly(paperweight.paperDevBundle("1.21-R0.1-SNAPSHOT"))
    paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
    // paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:1.19-R0.1-SNAPSHOT")

    implementation(project(":common:common-core"))

    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.11") {
        exclude("org.yaml")
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.11")
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

    jar {
        archiveClassifier.set("deobf")
    }

    shadowJar {
        archiveClassifier.set("deobf-all")
    }

    reobfJar {
        inputJar.set(shadowJar.flatMap { it.archiveFile })
    }
}

otgPlatform {
    productionJar.set(tasks.reobfJar.flatMap { it.outputJar })
}
