rootProject.name = "HypixelSkyBlock"
include(":packer")
include(":commons")
include(":dungeons")

include(":velocity.extension")
include(":proxy.api")

include(":service.generic")
include(":type.generic")
include(":type.skyblockgeneric")
include(":type.lobby")
include(":type.game")
include(":spark")
include(":loader")
include(":pvp")

include(":type.ravengardgeneric")
include(":type.ravengardlobby")
include(":type.ravengarddungeon")

include(":service.auctionhouse")
include(":service.bazaar")
include(":service.itemtracker")
include(":service.api")
include(":service.party")
include(":service.orchestrator")
include(":service.darkauction")
include(":service.friend")
include(":service.replay")
include(":service.punishment")
include(":service.elections")
include(":service.guild")
include(":service.store")
include(":anticheat")

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
