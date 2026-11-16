rootProject.name = "Ravengard"
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

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}
