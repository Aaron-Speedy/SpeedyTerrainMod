pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.minecraftforge.net/")
    }

    includeBuild("build-logic")
}

rootProject.name = "OpenTerrainGenerator"

include(
    "common:common-util",
    "common:common-config",
    "common:common-customobject",
    "common:common-generator",
    "common:common-core",
    //"platforms:forge",
    //"platforms:fabric",
    "platforms:paper",
)
