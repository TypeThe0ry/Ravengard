package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.jetbrains.annotations.Nullable;

public final class RavengardOnlyRouting {
    private RavengardOnlyRouting() {
    }

    public static ServerType lobbyDestination(@Nullable ServerType currentType, @Nullable ServerType requestedType) {
        if (requestedType == null || requestedType == ServerType.PROTOTYPE_LOBBY) {
            return ServerType.RAVENGARD_LOBBY;
        }
        return requestedType;
    }

    public static ServerType afkOrigin(@Nullable ServerType originType, @Nullable ServerType currentType) {
        if (originType != null && originType.name().endsWith("_LOBBY")) {
            return originType == ServerType.PROTOTYPE_LOBBY ? ServerType.RAVENGARD_LOBBY : originType;
        }
        if (currentType != null && currentType.name().endsWith("_LOBBY")) {
            return currentType == ServerType.PROTOTYPE_LOBBY ? ServerType.RAVENGARD_LOBBY : currentType;
        }
        return ServerType.RAVENGARD_LOBBY;
    }
}
