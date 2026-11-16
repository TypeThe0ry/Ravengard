package net.swofty.type.generic.data.domain;

import net.swofty.commons.ServerType;
import net.swofty.commons.data.SwoftyData;
import net.minestom.server.entity.PlayerSkin;
import net.swofty.proxyapi.PlayerTransferDataCache;
import net.swofty.type.generic.HypixelConst;
import net.swofty.type.generic.achievement.AchievementStatisticsService;
import net.swofty.type.generic.data.HypixelDataHandler;
import net.swofty.type.generic.data.datapoints.DatapointLocale;
import net.swofty.type.generic.data.datapoints.DatapointString;
import net.swofty.type.generic.entity.hologram.PlayerHolograms;
import net.swofty.type.generic.entity.npc.HypixelNPC;
import net.swofty.type.generic.resourcepack.ResourcePackManager;
import net.swofty.type.generic.user.HypixelPlayer;
import org.bson.Document;

import java.util.UUID;

public final class AccountDomain implements PlayerDataDomain<HypixelDataHandler> {
    public static final DomainKey<HypixelDataHandler> KEY = new DomainKey<>("account", HypixelDataHandler.class);

    @Override
    public DomainKey<HypixelDataHandler> key() {
        return KEY;
    }

    @Override
    public boolean appliesTo(ServerType type) {
        return true;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public void load(UUID uuid) {
        if (PlayerDataService.isLoaded(KEY, uuid)) return;
        HypixelDataHandler handler = HypixelDataHandler.initUserWithDefaultData(uuid);

        String transferred = PlayerTransferDataCache.takeAccountDocument(uuid);
        if (transferred != null) {
            handler.loadFromTransferDocument(Document.parse(transferred));
        } else {
            handler.loadFromApi();
        }

        PlayerDataService.store(KEY, uuid, handler);
        AchievementStatisticsService.recordPlayer(uuid);
    }

    @Override
    public void applyToPlayer(HypixelPlayer player) {
        HypixelDataHandler handler = PlayerDataService.get(KEY, player.getUuid());

        player.setLocale(handler.get(HypixelDataHandler.Data.LOCALE, DatapointLocale.class)
                .getValue().getCurrentLocale().getLocale());
        if (player.getSkin() == null) {
            PlayerSkin storedSkin = storedSkin(handler);
            if (storedSkin != null) player.setSkin(storedSkin);
        }
        handler.runOnLoad(player);

        ResourcePackManager packManager = HypixelConst.getResourcePackManager();
        if (packManager != null) {
            packManager.getActivePack().onPlayerJoin(player);
        }

        HypixelNPC.updateForPlayer(player);
        PlayerHolograms.spawnAll(player);
    }

    @Override
    public void save(HypixelPlayer player) {
        HypixelDataHandler handler = PlayerDataService.find(KEY, player.getUuid()).orElse(null);
        if (handler == null) return;
        handler.runOnSave(player);
        handler.saveToApi();
        Leaderboards.sync(player.getUuid(), handler);
    }

    @Override
    public void cleanup(HypixelPlayer player) {
        ResourcePackManager packManager = HypixelConst.getResourcePackManager();
        if (packManager != null) {
            packManager.getActivePack().onPlayerQuit(player);
        }
    }

    @Override
    public void unload(UUID uuid) {
        SwoftyData.account().unload(uuid);
        PlayerDataService.evict(KEY, uuid);
    }

    public static String transferDocument(HypixelPlayer player) {
        HypixelDataHandler handler = PlayerDataService.find(KEY, player.getUuid()).orElse(null);
        if (handler == null) throw new IllegalStateException("Account data is not loaded");

        SwoftyData.account().flush(player.getUuid());
        return handler.toDocument().toJson();
    }

    static PlayerSkin storedSkin(HypixelDataHandler handler) {
        String textures = handler.get(HypixelDataHandler.Data.SKIN_TEXTURE, DatapointString.class).getValue();
        if (textures == null || textures.isBlank() || textures.equals("null")) return null;

        String signature = handler.get(HypixelDataHandler.Data.SKIN_SIGNATURE, DatapointString.class).getValue();
        if (signature == null || signature.isBlank() || signature.equals("null")) signature = null;
        return new PlayerSkin(textures, signature);
    }
}
