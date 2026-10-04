package net.swofty.type.generic.command.commands;

import net.swofty.commons.ServerType;
import net.swofty.proxyapi.ProxyPlayer;
import net.swofty.type.generic.HypixelConst;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.data.PlayerWipeService;
import net.swofty.type.generic.data.domain.PlayerDataService;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.generic.utility.ScheduleUtility;
import org.tinylog.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@CommandParameters(description = "Allows the player to wipe themselves",
        usage = "/wipeme",
        permission = Rank.STAFF,
        labels = "deletemyprofiles wipeme",
        allowsConsole = false)
public class WipeMeCommand extends HypixelCommand {
    private static final ServerType DESTINATION = ServerType.PROTOTYPE_LOBBY;
    private static final long TRANSFER_TIMEOUT_SECONDS = 20;

    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, _) -> {
            if (!permissionCheck(sender)) return;

            HypixelPlayer player = (HypixelPlayer) sender;
            UUID playerUuid = player.getUuid();
            if (!PlayerWipeService.begin(playerUuid)) {
                player.sendMessage("<c>你的数据正在清除中。");
                return;
            }

            ServerType type = HypixelConst.getTypeLoader().getType();
            player.sendMessage("<c>正在清除你在所有服务器上的全部数据。");
            Thread.startVirtualThread(() -> wipe(player, playerUuid, type));
        });
    }

    private void wipe(HypixelPlayer player, UUID playerUuid, ServerType type) {
        try {
            PlayerDataService.discardAll(type, playerUuid);
            PlayerWipeService.Result result = PlayerWipeService.wipe(playerUuid);
            Logger.info("Wiped {} profile(s), {} island(s), {} leaderboard entr(ies) and {} tracked row(s) of {}",
                    result.profiles().size(), result.islands(), result.leaderboards(), result.ownedRows(), playerUuid);

            if (type == DESTINATION) {
                reload(player, playerUuid, type);
                return;
            }

            new ProxyPlayer(playerUuid).transferWithoutDataTo(DESTINATION)
                    .orTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(error -> {
                        Logger.error(error, "Failed to move wiped user {} to the prototype lobby", playerUuid);
                        kick(player);
                        return null;
                    });
        } catch (Exception e) {
            Logger.error(e, "Failed to wipe user {}", playerUuid);
            kick(player);
        } finally {
            PlayerWipeService.finish(playerUuid);
        }
    }

    private void reload(HypixelPlayer player, UUID playerUuid, ServerType type) {
        try {
            PlayerDataService.loadAll(type, playerUuid);
            PlayerDataService.attachAll(type, player);
            PlayerDataService.applyAll(type, player);
            player.sendMessage("<a>你的数据已清除。");
        } catch (Exception e) {
            Logger.error(e, "Failed to reload wiped user {}", playerUuid);
            kick(player);
        }
    }

    private void kick(HypixelPlayer player) {
        ScheduleUtility.nextTick(() -> {
            if (player.isOnline()) player.kick("<c>你的数据已清除");
        });
    }
}
