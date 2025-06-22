plugins {
    id("parent-logic")
}

defaultTasks = arrayListOf("build", "publishToMavenLocal")

allprojects {
    group = "com.pg85.otg"
    version = "1.21-0.0.50"
    description = "SpeedyTerrainMod"
}

subprojects {
    apply(plugin = "base-conventions")

    repositories {
        mavenCentral()
    }
}

val universalJar = tasks.register<Jar>("universalJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("SpeedyTerrainMod-Universal-" + project.version + ".jar")
}

tasks.build {
    dependsOn(universalJar)
}

listOf(
    project(":platforms:paper"),
    //project(":platforms:forge"),
    // project(":platforms:fabric"),
).forEach { proj ->
    proj.afterEvaluate {
        // Show more errors in intellij
        proj.tasks.withType<JavaCompile>() {
            options.compilerArgs.add("-Xmaxerrs")
            options.compilerArgs.add("5000")
        }
        universalJar {
            val tree = zipTree(proj.the<OTGPlatformExtension>().productionJar)
            from(tree)
            val manifestFile = tree.elements.map { files ->
                files.find { it.asFile.path.endsWith("META-INF/MANIFEST.MF") }!!
            }
            manifest.from(manifestFile)
        }
    }
}
