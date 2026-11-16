package net.swofty.commons;

import java.nio.file.Path;

public enum CustomWorlds {
    RAVENGARD_LOBBY("hypixel_ravengard_lobby"),
    RAVENGARD_TUTORIAL("hypixel_ravengard_tutorial"),
	;

    private final String fileName;

    CustomWorlds(String fileName) {
        this.fileName = fileName;
	}

	public Path getPath() {
        return Path.of("./configuration/world/" + fileName + ".polar");
	}
}
