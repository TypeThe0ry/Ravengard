rootProject.name = "HypixelSkyBlock"
include(":packer")
include(":commons")
include(":dungeons")

include(":velocity.extension")
include(":proxy.api")

include(":service.generic")
include(":type.generic")
include(":type.game")
include(":loader")

include(":type.ravengardgeneric")
include(":type.ravengardlobby")
include(":type.ravengarddungeon")

include(":service.party")
include(":service.orchestrator")

val clientModRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName == "clientmod" ||
        taskName == ":clientmod" ||
        taskName.startsWith("clientmod:") ||
        taskName.startsWith(":clientmod:")
}

if (clientModRequested || gradle.startParameter.projectProperties.containsKey("includeClientmod")) {
    include(":clientmod")
}

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}
