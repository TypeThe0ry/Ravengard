package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.MinecraftServer;
import net.swofty.type.ravengardgeneric.item.RavengardItemConfigParser;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPlayerCombatCooldownTest {
    @Test
    void rapidAttacksAreDroppedThenScaledUntilCooldownCompletes() {
        UUID player = UUID.randomUUID();
        assertEquals(1.0, ActionPlayerCombat.attackStrength(player, 1_000_000_000L, 4.0), 0.0001);
        assertEquals(-1.0, ActionPlayerCombat.attackStrength(player, 1_050_000_000L, 4.0), 0.0001);
        assertEquals(0.5, ActionPlayerCombat.attackStrength(player, 1_125_000_000L, 4.0), 0.0001);
        assertEquals(1.0, ActionPlayerCombat.attackStrength(player, 1_400_000_000L, 4.0), 0.0001);
    }

    @Test
    void aWeaponSwingsAtTheAttackSpeedItsDataGivesAndFistsAtFour() {
        MinecraftServer.init();
        RavengardItemType halberd = RavengardItemConfigParser.parse(java.util.Map.of(
                "id", "TEST_HALBERD", "rarity", "COMMON", "material", "stick",
                "default_statistics", java.util.Map.of("damage", 50.0, "attack_speed", 1.0)));
        assertEquals(1.0, ActionPlayerCombat.attackSpeed(halberd), 1e-9);
        assertEquals(4.0, ActionPlayerCombat.attackSpeed(null), 1e-9);

        UUID player = UUID.randomUUID();
        assertEquals(1.0, ActionPlayerCombat.attackStrength(player, 0L, 1.0), 1e-9);
        assertEquals(-1.0, ActionPlayerCombat.attackStrength(player, 300_000_000L, 1.0), 1e-9);
        assertEquals(0.6, ActionPlayerCombat.attackStrength(player, 600_000_000L, 1.0), 1e-9);
    }

    @Test
    void aStaffBoltWaitsOutItsRangedAttackSpeedAndAccelerateHalvesIt() {
        MinecraftServer.init();
        RavengardItemType staff = RavengardItemConfigParser.parse(java.util.Map.of(
                "id", "TEST_STAFF", "rarity", "COMMON", "material", "stick",
                "default_statistics", java.util.Map.of("ranged_damage", 30.0, "ranged_attack_speed", 0.5)));
        assertEquals(2000, ActionPlayerStaff.cooldownMillis(staff, 1.0));
        assertEquals(1000, ActionPlayerStaff.cooldownMillis(staff, 0.5));
    }
    @Test
    void clickingFastNeverBeatsWaitingForTheWeaponToRecover() {
        for (double speed : new double[]{0.5, 1.0, 2.0, 4.0}) {
            double interval = 1.0 / speed;
            double patient = interval > 0 ? 1.0 / interval : 0;
            for (double clicksPerSecond : new double[]{4, 8, 12, 20}) {
                UUID player = UUID.randomUUID();
                long gap = (long) (1_000_000_000L / clicksPerSecond);
                double total = 0;
                ActionPlayerCombat.attackStrength(player, 0L, speed);
                for (int click = 1; click <= 200; click++) {
                    double strength = ActionPlayerCombat.attackStrength(player, click * gap, speed);
                    if (strength >= 0) total += strength * strength;
                }
                double perSecond = total / (200.0 / clicksPerSecond);
                assertTrue(perSecond <= patient * 1.05,
                        "speed " + speed + " clicking " + clicksPerSecond + "/s dealt " + perSecond
                                + " full hits a second, waiting deals " + patient);
            }
        }
    }

    @Test
    void fastWeaponsNeedLessRecoveryThanHeavyOnes() {
        assertEquals(0.5, ActionPlayerCombat.minimumRecovery(0.5), 1e-9);
        assertEquals(0.45, ActionPlayerCombat.minimumRecovery(1.0), 1e-9);
        assertEquals(0.25, ActionPlayerCombat.minimumRecovery(4.0), 1e-9);
    }
}
