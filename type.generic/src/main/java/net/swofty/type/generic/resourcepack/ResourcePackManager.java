package net.swofty.type.generic.resourcepack;

import lombok.Getter;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.swofty.commons.text.Text;
import net.minestom.server.entity.Player;
import org.tinylog.Logger;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ResourcePackManager {
    @Getter
    private static ResourcePackManager instance;

    @Getter
    private final HypixelResourcePack activePack;

    public ResourcePackManager(HypixelResourcePack pack) {
        this.activePack = pack;
        instance = this;
    }

    public void initialize() {
        activePack.initialize();
    }

    public void sendPack(Player player) {
        sendPackBlocking(player, 0);
    }

    /**
     * Pushes the pack and, when {@code timeoutSeconds > 0}, waits for the client's
     * terminal pack status. The pack must finish applying during the configuration
     * phase, before any world data streams: a mid-game resource reload leaves
     * already-received chunk sections permanently unrendered on the client.
     */
    public void sendPackBlocking(Player player, int timeoutSeconds) {
        String packUrl = activePack.getPackUrl();
        String packHash = activePack.getPackHash();

        if (packUrl == null || packUrl.isEmpty() || packHash == null || packHash.isEmpty()) {
            Logger.warn("Resource pack URL or hash not configured, skipping pack send for " + player.getUsername());
            return;
        }

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                activePack.getPackId(),
                URI.create(packUrl),
                packHash
        );

        CompletableFuture<Void> resolved = new CompletableFuture<>();
        java.util.List<ResourcePackInfo> packs = new java.util.ArrayList<>();
        packs.add(info);
        java.util.Set<UUID> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
        packs.forEach(pack -> pending.add(pack.id()));
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(packs)
                .replace(true)
                .required(activePack.isRequired())
                .prompt(Text.of("<a>This resource pack is required to play on Hypixel.").asComponent())
                .callback((packId, status, audience) -> {
                    if (status.intermediate() || !pending.remove(packId)) return;
                    if (status != net.kyori.adventure.resource.ResourcePackStatus.SUCCESSFULLY_LOADED) {
                        Logger.warn("Resource pack {} for {} finished with status {}",
                                packId, player.getUsername(), status);
                    } else if (packId.equals(info.id())) {
                        player.getPlayerConnection().storeCookie("hypixel:applied_pack",
                                packHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if (pending.isEmpty()) resolved.complete(null);
                })
                .build();

        Logger.info("Sending resource pack {} to {} from {}",
                packHash.substring(0, 8), player.getUsername(), packUrl);
        player.sendResourcePacks(request);

        if (timeoutSeconds > 0) {
            boolean completed = awaitResolution(resolved, timeoutSeconds, TimeUnit.SECONDS,
                    () -> java.util.List.copyOf(pending).forEach(packId -> player.onResourcePackStatus(
                            packId, net.kyori.adventure.resource.ResourcePackStatus.DISCARDED)));
            if (!completed) {
                Logger.warn("Resource pack for {} did not resolve within {}s, continuing",
                        player.getUsername(), timeoutSeconds);
            }
        }
    }

    static boolean awaitResolution(CompletableFuture<Void> resolved, long timeout, TimeUnit unit,
                                   Runnable timeoutFallback) {
        try {
            resolved.get(timeout, unit);
            return true;
        } catch (TimeoutException exception) {
            timeoutFallback.run();
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            timeoutFallback.run();
            return false;
        } catch (java.util.concurrent.ExecutionException exception) {
            timeoutFallback.run();
            return false;
        }
    }
}
