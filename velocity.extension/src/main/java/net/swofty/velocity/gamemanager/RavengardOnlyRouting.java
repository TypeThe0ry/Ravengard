package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.jetbrains.annotations.Nullable;

public final class RavengardOnlyRouting {
    private RavengardOnlyRouting() {
    }

    public static ServerType lobbyDestination(@Nullable ServerType currentType, @Nullable ServerType requestedType) {
        return requestedType == null ? ServerType.RAVENGARD_LOBBY : requestedType;
    }

    public static ServerType afkOrigin(@Nullable ServerType originType, @Nullable ServerType currentType) {
        if (originType == ServerType.RAVENGARD_LOBBY) {
            return originType;
        }
        if (currentType == ServerType.RAVENGARD_LOBBY) {
            return currentType;
        }
        return ServerType.RAVENGARD_LOBBY;
    }
}
