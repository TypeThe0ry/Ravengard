package net.swofty.type.generic.data.domain;

import net.minestom.server.entity.PlayerSkin;
import net.swofty.type.generic.data.HypixelDataHandler;
import net.swofty.type.generic.data.datapoints.DatapointString;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountDomainTest {
    @Test
    void restoresStoredSkinForOfflinePlayers() {
        HypixelDataHandler handler = HypixelDataHandler.initUserWithDefaultData(UUID.randomUUID());
        handler.get(HypixelDataHandler.Data.SKIN_TEXTURE, DatapointString.class).deserializeValue("\"textures\"");
        handler.get(HypixelDataHandler.Data.SKIN_SIGNATURE, DatapointString.class).deserializeValue("\"signature\"");

        assertEquals(new PlayerSkin("textures", "signature"), AccountDomain.storedSkin(handler));
    }

    @Test
    void leavesPlayersWithoutStoredSkinUnskinned() {
        HypixelDataHandler handler = HypixelDataHandler.initUserWithDefaultData(UUID.randomUUID());

        assertNull(AccountDomain.storedSkin(handler));
    }
}
