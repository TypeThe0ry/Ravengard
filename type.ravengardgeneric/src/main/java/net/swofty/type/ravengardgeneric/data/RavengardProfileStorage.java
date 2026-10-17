package net.swofty.type.ravengardgeneric.data;

import net.swofty.PlayerField;
import net.swofty.commons.data.SwoftyData;
import net.swofty.transaction.Transaction;
import net.swofty.type.ravengardgeneric.classes.RavengardClass;
import net.swofty.type.ravengardgeneric.profile.RavengardProfile;
import org.bson.Document;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RavengardProfileStorage {
    private RavengardProfileStorage() {
    }

    public static UUID owner(UUID profileId) {
        if (profileId == null) return null;
        try {
            return readOwner(profileId);
        } finally {
            release(profileId);
        }
    }

    public static boolean exists(UUID profileId) {
        return owner(profileId) != null;
    }

    public static RavengardProfile byId(UUID profileId) {
        if (profileId == null) return null;
        try {
            UUID owner = readOwner(profileId);
            if (owner == null) return null;

            RavengardProfile profile = new RavengardProfile(profileId, owner);
            profile.setCreated(readLong(profileId, RavengardProfileFields.CREATED, profile.getCreated()));
            profile.setProfileClass(RavengardClass.fromKey(
                    readString(profileId, RavengardProfileFields.CLASS, "")));
            profile.setLevel(readInt(profileId, RavengardProfileFields.LEVEL, 1));
            profile.setExperience(readInt(profileId, RavengardProfileFields.EXPERIENCE, 0));
            profile.setCrowns(readInt(profileId, RavengardProfileFields.CROWNS, 0));
            profile.setAbilityPoints(readInt(profileId, RavengardProfileFields.ABILITY_POINTS, 0));
            profile.setTutorial(readBoolean(profileId, RavengardProfileFields.TUTORIAL, true));
            profile.setPlaytimeSeconds(readLong(profileId, RavengardProfileFields.PLAYTIME_SECONDS, 0L));
            profile.getIntros().addAll(decodeNames(read(profileId, RavengardProfileFields.INTROS)));
            profile.getDiscoveredRegions().addAll(decodeNames(
                    read(profileId, RavengardProfileFields.DISCOVERED_REGIONS)));
            profile.getInventory().putAll(decodeInventory(read(profileId, RavengardProfileFields.INVENTORY)));
            profile.getLockBox().putAll(decodeInventory(read(profileId, RavengardProfileFields.LOCK_BOX)));
            profile.setLockBoxTier(readInt(profileId, RavengardProfileFields.LOCK_BOX_TIER, 1));
            return profile;
        } finally {
            release(profileId);
        }
    }

    public static RavengardProfile create(UUID profileId, UUID owner) {
        RavengardProfile profile = new RavengardProfile(profileId, owner);
        writeAll(profile);
        return profile;
    }

    public static void writeAll(RavengardProfile profile) {
        UUID profileId = profile.getId();
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                writeAll(transaction, profile);
            });
        } finally {
            release(profileId);
        }
    }

    public static void delete(UUID profileId) {
        if (profileId == null) return;
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                for (PlayerField<String> field : RavengardProfileFields.PROFILE_FIELDS) {
                    transaction.set(field, null);
                }
                for (PlayerField<String> field : RavengardProfileFields.ABILITY_FIELDS) {
                    transaction.set(field, null);
                }
            });
        } finally {
            unload(profileId);
        }
    }

    /** What an experience award did to a profile: where its level and experience ended up, and how many levels it gained. */
    public record Progress(int level, int experience, int levelsGained) {
    }

    /**
     * Adds experience to one profile, carrying it over into levels the way the menus show it
     * (a level takes ten times itself) and giving an ability point for every level gained. Only
     * the three fields it changes are written, so it is safe while a run holds the rest.
     */
    public static Progress addExperience(UUID profileId, int amount) {
        if (profileId == null || amount <= 0) return null;
        Progress[] result = new Progress[1];
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                int level = decodeInt(transaction.get(RavengardProfileFields.LEVEL), 1);
                int experience = decodeInt(transaction.get(RavengardProfileFields.EXPERIENCE), 0) + amount;
                int points = decodeInt(transaction.get(RavengardProfileFields.ABILITY_POINTS), 0);
                int gained = 0;
                while (experience >= level * 10) {
                    experience -= level * 10;
                    level++;
                    points++;
                    gained++;
                }
                transaction.set(RavengardProfileFields.LEVEL, RavengardProfileFields.INTEGER.serialize(level));
                transaction.set(RavengardProfileFields.EXPERIENCE, RavengardProfileFields.INTEGER.serialize(experience));
                transaction.set(RavengardProfileFields.ABILITY_POINTS, RavengardProfileFields.INTEGER.serialize(points));
                result[0] = new Progress(level, experience, gained);
            });
        } finally {
            release(profileId);
        }
        return result[0];
    }

    public static void addPlaytime(UUID profileId, long seconds) {
        if (profileId == null || seconds <= 0) return;
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                long current = decodeLong(transaction.get(RavengardProfileFields.PLAYTIME_SECONDS), 0L);
                transaction.set(RavengardProfileFields.PLAYTIME_SECONDS,
                        RavengardProfileFields.LONG.serialize(current + seconds));
            });
        } finally {
            release(profileId);
        }
    }

    public static Map<Integer, String> readInventory(UUID profileId) {
        if (profileId == null) return Map.of();
        try {
            return decodeInventory(read(profileId, RavengardProfileFields.INVENTORY));
        } finally {
            release(profileId);
        }
    }

    public static void writeInventory(UUID profileId, Map<Integer, String> inventory) {
        if (profileId == null) return;
        try {
            SwoftyData.profile().set(profileId, RavengardProfileFields.INVENTORY, encodeInventory(inventory));
        } finally {
            release(profileId);
        }
    }

    public static Map<Integer, String> readLockBox(UUID profileId) {
        if (profileId == null) return Map.of();
        try {
            return decodeInventory(read(profileId, RavengardProfileFields.LOCK_BOX));
        } finally {
            release(profileId);
        }
    }

    public static void writeLockBox(UUID profileId, Map<Integer, String> contents) {
        if (profileId == null) return;
        try {
            SwoftyData.profile().set(profileId, RavengardProfileFields.LOCK_BOX, encodeInventory(contents));
        } finally {
            release(profileId);
        }
    }

    public static int lockBoxTier(UUID profileId) {
        if (profileId == null) return 1;
        try {
            return Math.max(1, decodeInt(read(profileId, RavengardProfileFields.LOCK_BOX_TIER), 1));
        } finally {
            release(profileId);
        }
    }

    public static void writeLockBoxTier(UUID profileId, int tier) {
        if (profileId == null) return;
        try {
            SwoftyData.profile().set(profileId, RavengardProfileFields.LOCK_BOX_TIER,
                    RavengardProfileFields.INTEGER.serialize(tier));
        } finally {
            release(profileId);
        }
    }

    /** The abilities a profile has bought, the two it has equipped in order, and its unspent points. */
    public record AbilityState(Set<String> unlocked, List<String> equipped, int points) {
    }

    public static AbilityState abilities(UUID profileId) {
        if (profileId == null) return new AbilityState(Set.of(), List.of(), 0);
        try {
            return new AbilityState(
                    new LinkedHashSet<>(decodeNames(read(profileId, RavengardProfileFields.UNLOCKED_ABILITIES))),
                    decodeNames(read(profileId, RavengardProfileFields.EQUIPPED_ABILITIES)),
                    readInt(profileId, RavengardProfileFields.ABILITY_POINTS, 0));
        } finally {
            release(profileId);
        }
    }

    /** Spends one ability point on an ability; false when there is no point to spend or it is already owned. */
    public static boolean unlockAbility(UUID profileId, String ability) {
        if (profileId == null || ability == null) return false;
        boolean[] unlocked = new boolean[1];
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                int points = decodeInt(transaction.get(RavengardProfileFields.ABILITY_POINTS), 0);
                Set<String> names = new LinkedHashSet<>(decodeNames(transaction.get(RavengardProfileFields.UNLOCKED_ABILITIES)));
                if (points < 1 || !names.add(ability)) return;
                transaction.set(RavengardProfileFields.ABILITY_POINTS, RavengardProfileFields.INTEGER.serialize(points - 1));
                transaction.set(RavengardProfileFields.UNLOCKED_ABILITIES, encodeNames(names));
                unlocked[0] = true;
            });
        } finally {
            release(profileId);
        }
        return unlocked[0];
    }

    public static void equipAbilities(UUID profileId, String first, String second) {
        if (profileId == null) return;
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                transaction.set(RavengardProfileFields.EQUIPPED_ABILITIES,
                        RavengardProfileFields.STRING_ARRAY.serialize(new String[]{first, second}));
            });
        } finally {
            release(profileId);
        }
    }

    public static boolean hasIntro(UUID profileId, String npc) {
        return containsName(profileId, RavengardProfileFields.INTROS, npc);
    }

    public static void addIntro(UUID profileId, String npc) {
        addName(profileId, RavengardProfileFields.INTROS, npc);
    }

    public static boolean hasDiscoveredRegion(UUID profileId, String region) {
        return containsName(profileId, RavengardProfileFields.DISCOVERED_REGIONS, region);
    }

    public static void addDiscoveredRegion(UUID profileId, String region) {
        addName(profileId, RavengardProfileFields.DISCOVERED_REGIONS, region);
    }

    private static boolean containsName(UUID profileId, PlayerField<String> field, String name) {
        if (profileId == null) return false;
        try {
            return decodeNames(read(profileId, field)).contains(name);
        } finally {
            release(profileId);
        }
    }

    private static void addName(UUID profileId, PlayerField<String> field, String name) {
        if (profileId == null || name == null) return;
        try {
            SwoftyData.profile().transaction(profileId, transaction -> {
                Set<String> names = new LinkedHashSet<>(decodeNames(transaction.get(field)));
                if (!names.add(name)) return;
                transaction.set(field, encodeNames(names));
            });
        } finally {
            release(profileId);
        }
    }

    public static void release(UUID profileId) {
        if (profileId == null || RavengardDomain.isProfileHosted(profileId)) return;
        unload(profileId);
    }

    static String encodeInventory(Map<Integer, String> inventory) {
        Document document = new Document();
        inventory.forEach((slot, snbt) -> document.put(String.valueOf(slot), snbt));
        return document.toJson();
    }

    static Map<Integer, String> decodeInventory(String stored) {
        Map<Integer, String> inventory = new HashMap<>();
        if (stored == null || stored.isEmpty()) return inventory;
        try {
            Document.parse(stored).forEach((slot, snbt) -> {
                if (snbt instanceof String value) inventory.put(Integer.parseInt(slot), value);
            });
        } catch (Exception exception) {
            Logger.warn(exception, "Could not decode a stored Ravengard inventory");
        }
        return inventory;
    }

    static String encodeNames(Set<String> names) {
        return RavengardProfileFields.STRING_ARRAY.serialize(names.toArray(String[]::new));
    }

    static List<String> decodeNames(String stored) {
        if (stored == null || stored.isEmpty()) return List.of();
        try {
            return new ArrayList<>(List.of(RavengardProfileFields.STRING_ARRAY.deserialize(stored)));
        } catch (Exception exception) {
            Logger.warn(exception, "Could not decode a stored Ravengard name list");
            return List.of();
        }
    }

    static int decodeInt(String stored, int fallback) {
        if (stored == null || stored.isEmpty()) return fallback;
        try {
            Integer value = RavengardProfileFields.INTEGER.deserialize(stored);
            return value == null ? fallback : value;
        } catch (Exception exception) {
            return fallback;
        }
    }

    static long decodeLong(String stored, long fallback) {
        if (stored == null || stored.isEmpty()) return fallback;
        try {
            Long value = RavengardProfileFields.LONG.deserialize(stored);
            return value == null ? fallback : value;
        } catch (Exception exception) {
            return fallback;
        }
    }

    static boolean decodeBoolean(String stored, boolean fallback) {
        if (stored == null || stored.isEmpty()) return fallback;
        try {
            Boolean value = RavengardProfileFields.BOOLEAN.deserialize(stored);
            return value == null ? fallback : value;
        } catch (Exception exception) {
            return fallback;
        }
    }

    static String decodeString(String stored, String fallback) {
        if (stored == null || stored.isEmpty()) return fallback;
        try {
            String value = RavengardProfileFields.STRING.deserialize(stored);
            return value == null ? fallback : value;
        } catch (Exception exception) {
            return fallback;
        }
    }

    static void writeAll(Transaction transaction, RavengardProfile profile) {
        RavengardClass profileClass = profile.getProfileClass();
        transaction.set(RavengardProfileFields.OWNER,
                RavengardProfileFields.STRING.serialize(profile.getOwner().toString()));
        transaction.set(RavengardProfileFields.CREATED,
                RavengardProfileFields.LONG.serialize(profile.getCreated()));
        transaction.set(RavengardProfileFields.CLASS,
                RavengardProfileFields.STRING.serialize(profileClass == null ? "" : profileClass.name()));
        transaction.set(RavengardProfileFields.LEVEL,
                RavengardProfileFields.INTEGER.serialize(profile.getLevel()));
        transaction.set(RavengardProfileFields.EXPERIENCE,
                RavengardProfileFields.INTEGER.serialize(profile.getExperience()));
        transaction.set(RavengardProfileFields.CROWNS,
                RavengardProfileFields.INTEGER.serialize(profile.getCrowns()));
        transaction.set(RavengardProfileFields.ABILITY_POINTS,
                RavengardProfileFields.INTEGER.serialize(profile.getAbilityPoints()));
        transaction.set(RavengardProfileFields.TUTORIAL,
                RavengardProfileFields.BOOLEAN.serialize(profile.isTutorial()));
        transaction.set(RavengardProfileFields.PLAYTIME_SECONDS,
                RavengardProfileFields.LONG.serialize(profile.getPlaytimeSeconds()));
        transaction.set(RavengardProfileFields.INTROS, encodeNames(profile.getIntros()));
        transaction.set(RavengardProfileFields.DISCOVERED_REGIONS,
                encodeNames(profile.getDiscoveredRegions()));
        transaction.set(RavengardProfileFields.INVENTORY, encodeInventory(profile.getInventory()));
        transaction.set(RavengardProfileFields.LOCK_BOX, encodeInventory(profile.getLockBox()));
        transaction.set(RavengardProfileFields.LOCK_BOX_TIER,
                RavengardProfileFields.INTEGER.serialize(profile.getLockBoxTier()));
    }

    private static UUID readOwner(UUID profileId) {
        String stored = decodeString(read(profileId, RavengardProfileFields.OWNER), null);
        if (stored == null || stored.isEmpty()) return null;
        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException exception) {
            Logger.warn("Ravengard profile {} stores an unreadable owner {}", profileId, stored);
            return null;
        }
    }

    private static String read(UUID profileId, PlayerField<String> field) {
        return SwoftyData.profile().get(profileId, field);
    }

    private static String readString(UUID profileId, PlayerField<String> field, String fallback) {
        return decodeString(read(profileId, field), fallback);
    }

    private static int readInt(UUID profileId, PlayerField<String> field, int fallback) {
        return decodeInt(read(profileId, field), fallback);
    }

    private static long readLong(UUID profileId, PlayerField<String> field, long fallback) {
        return decodeLong(read(profileId, field), fallback);
    }

    private static boolean readBoolean(UUID profileId, PlayerField<String> field, boolean fallback) {
        return decodeBoolean(read(profileId, field), fallback);
    }

    private static void unload(UUID profileId) {
        try {
            SwoftyData.profile().unload(profileId);
        } catch (Exception exception) {
            Logger.error(exception, "Failed to release the Ravengard profile container {}", profileId);
        }
    }
}
