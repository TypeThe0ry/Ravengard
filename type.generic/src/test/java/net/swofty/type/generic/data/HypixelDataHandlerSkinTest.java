package net.swofty.type.generic.data;

import net.minestom.server.entity.PlayerSkin;
import net.swofty.type.generic.data.datapoints.DatapointString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HypixelDataHandlerSkinTest {
    @Test
    void missingOfflineSkinPreservesStoredValue() {
        DatapointString datapoint = new DatapointString("skin_texture", "stored");

        HypixelDataHandler.updateSkinValue(null, datapoint, PlayerSkin::textures);

        assertEquals("stored", datapoint.getValue());
    }

    @Test
    void onlineSkinUpdatesStoredValue() {
        DatapointString datapoint = new DatapointString("skin_texture", "stored");

        HypixelDataHandler.updateSkinValue(new PlayerSkin("online", "signature"), datapoint, PlayerSkin::textures);

        assertEquals("online", datapoint.getValue());
    }
}
