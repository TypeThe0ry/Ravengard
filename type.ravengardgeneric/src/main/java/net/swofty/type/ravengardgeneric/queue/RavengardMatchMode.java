package net.swofty.type.ravengardgeneric.queue;

/**
 * How a dungeon run is played: alone or in teams of three. The name travels to the orchestrator as the game mode.
 * A solo run starts with a single player and speeds its countdown up once ten are in; a trios run waits for
 * two full teams and then counts down the short time.
 */
public enum RavengardMatchMode {
    SOLO("单人", 1, 12, 1, 10),
    TRIOS("三人组", 3, 9, 6, 6);

    public static final int DEFAULT_COUNTDOWN_SECONDS = 60;
    public static final int FAST_COUNTDOWN_SECONDS = 30;

    private final String displayName;
    private final int teamSize;
    private final int maxPlayers;
    private final int minPlayers;
    private final int fastStartPlayers;

    RavengardMatchMode(String displayName, int teamSize, int maxPlayers, int minPlayers, int fastStartPlayers) {
        this.displayName = displayName;
        this.teamSize = teamSize;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.fastStartPlayers = fastStartPlayers;
    }

    public int minPlayers() {
        return minPlayers;
    }

    /** From this many players on, the countdown runs the short time instead of the default one. */
    public int fastStartPlayers() {
        return fastStartPlayers;
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
