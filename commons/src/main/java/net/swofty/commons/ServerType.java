package net.swofty.commons;

import lombok.Getter;

@Getter
public enum ServerType {
	RAVENGARD_LOBBY,
	RAVENGARD_DUNGEON
	;

	public static boolean isServerType(String type) {
		for (ServerType a : values())
			if (type.equalsIgnoreCase(a.name())) return true;

		return false;
	}

	public String formatName() {
		return switch (this) {
			case RAVENGARD_LOBBY -> "天穹余烬大厅";
			case RAVENGARD_DUNGEON -> "天穹余烬地牢";
		};
	}
}
