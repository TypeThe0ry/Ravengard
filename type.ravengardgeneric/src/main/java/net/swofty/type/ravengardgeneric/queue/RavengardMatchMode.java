package net.swofty.type.ravengardgeneric.queue;

/** How a dungeon run is played: alone or in teams of three. The name travels to the orchestrator as the game mode. */
public enum RavengardMatchMode {
    SOLO("单人", 1, 8),
    TRIOS("三人组", 3, 9);

    private final String displayName;
    private final int teamSize;
    private final int maxPlayers;

    RavengardMatchMode(String displayName, int teamSize, int maxPlayers) {
        this.displayName = displayName;
        this.teamSize = teamSize;
        this.maxPlayers = maxPlayers;
    }

    public String displayName() {
        return displayName;
    }

    public int teamSize() {
        return teamSize;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public String queueName() {
        return "天穹余烬地牢·" + displayName;
    }

    /** The mode a run was created with; runs from before modes existed were trios. */
    public static RavengardMatchMode of(String mode) {
        if (mode == null) return TRIOS;
        for (RavengardMatchMode value : values()) {
            if (value.name().equalsIgnoreCase(mode)) return value;
        }
        return TRIOS;
    }

    public static boolean isMatchMode(String mode) {
        if (mode == null) return false;
        for (RavengardMatchMode value : values()) {
            if (value.name().equalsIgnoreCase(mode)) return true;
        }
        return "STANDARD".equalsIgnoreCase(mode);
    }
}
