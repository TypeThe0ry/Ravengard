package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengardgeneric.sound.RavengardSounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DungeonDoor extends DungeonInteractable {
    private static final String LEAF_MODEL = "hypixel_ravengard:item/gameplay/doors/door_1";
    private static final String TOP_MODEL = "hypixel_ravengard:item/gameplay/doors/door_1_top";
    private static final String IRON_GATE_MODEL = "hypixel_ravengard:item/gameplay/doors/iron_gate";
    private static final double IRON_GATE_ANCHOR_OFFSET = 0.036;
    private static final double IRON_GATE_LIFT = 3.75;
    private static final net.minestom.server.coordinate.Vec IRON_GATE_SCALE =
            new net.minestom.server.coordinate.Vec(2.0, 1.6, 1.6);
    private static final net.minestom.server.coordinate.Vec IRON_GATE_TRANSLATION =
            new net.minestom.server.coordinate.Vec(0.03125, -1.85, 0.0);
    private static final int SWING_TICKS = 6;
    private static final double WOODEN_SWING_ANGLE = 90.0;

    private final List<Leaf> leaves = new ArrayList<>();
    private final boolean verticalGate;
    private boolean swinging;
    private final Map<net.minestom.server.coordinate.Point, Boolean> closedBarriers = new HashMap<>();
    private Entity topDisplay;

    private static final Map<Instance, Map<Long, BarrierState>> BARRIERS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Instance, List<Set<DungeonDoor>>> OVERLAP_GROUPS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private record BarrierState(net.minestom.server.instance.block.Block original, int references) {
    }

    private record Leaf(Entity display, Pos closedPos, double hingeX, double hingeZ, int swingSign) {
    }

    private DungeonDoor(Instance instance) {
        this(instance, false);
    }

    private DungeonDoor(Instance instance, boolean verticalGate) {
        super(instance);
        this.verticalGate = verticalGate;
    }

    public static SpawnSummary spawnAll(Instance instance, List<RavengardDungeonConfig.DungeonObject> objects) {
        Map<String, List<RavengardDungeonConfig.DungeonObject>> leavesByCoordinate = new LinkedHashMap<>();
        Map<String, RavengardDungeonConfig.DungeonObject> topsByCoordinate = new LinkedHashMap<>();
        int inputMarkers = 0;
        for (RavengardDungeonConfig.DungeonObject object : objects) {
            if (object.type().equals("door_1_top")) {
                inputMarkers++;
                topsByCoordinate.putIfAbsent(coordinateKey(object), object);
            } else if (object.type().equals("door_1")) {
                inputMarkers++;
                String key = coordinateKey(object);
                List<RavengardDungeonConfig.DungeonObject> candidates =
                        leavesByCoordinate.computeIfAbsent(key, ignored -> new ArrayList<>());
                boolean duplicateYaw = candidates.stream().anyMatch(candidate ->
                        Math.abs(normalizedYaw(candidate.yaw()) - normalizedYaw(object.yaw())) < 0.001f);
                if (!duplicateYaw) {
                    candidates.add(object);
                }
            }
        }

        Set<String> consumedLeaves = new HashSet<>();
        Set<String> occupiedLeafCoordinates = new HashSet<>();
        List<DungeonDoor> spawnedDoors = new ArrayList<>();
        int completeDoorways = 0;
        int partialDoorways = 0;
        int topDisplays = 0;
        for (RavengardDungeonConfig.DungeonObject top : topsByCoordinate.values()) {
            List<RavengardDungeonConfig.DungeonObject> pair = new ArrayList<>(2);
            boolean axisAlongX = isAxisAlongX(top.yaw());
            double[][] offsets = axisAlongX
                    ? new double[][]{{top.x() - 0.5, top.z()}, {top.x() + 1.5, top.z()}}
                    : new double[][]{{top.x(), top.z() - 0.5}, {top.x(), top.z() + 1.5}};
            for (int index = 0; index < offsets.length; index++) {
                double[] offset = offsets[index];
                String key = coordinateKey(offset[0], top.y(), offset[1]);
                RavengardDungeonConfig.DungeonObject leaf = selectLeaf(
                        leavesByCoordinate.get(key), index == 0
                                ? normalizedYaw(top.yaw() + 180f)
                                : normalizedYaw(top.yaw()), consumedLeaves);
                if (leaf != null) {
                    consumedLeaves.add(markerKey(leaf));
                    occupiedLeafCoordinates.add(coordinateKey(leaf));
                    pair.add(leaf);
                }
            }
            if (!pair.isEmpty()) {
                spawnedDoors.add(spawnDoorway(instance, pair, top));
                if (pair.size() == 2) completeDoorways++;
                else partialDoorways++;
                topDisplays++;
            } else {
                StaticPiece staticPiece = new StaticPiece(instance);
                staticPiece.spawnTop(woodenTopPose(top));
                topDisplays++;
            }
        }

        int orphanLeaves = 0;
        Set<String> synthesizedOrphanLeaves = new HashSet<>();
        List<RavengardDungeonConfig.DungeonObject> orphanCandidates = leavesByCoordinate.values().stream()
                .flatMap(List::stream)
                .filter(candidate -> !consumedLeaves.contains(markerKey(candidate)))
                .filter(candidate -> !occupiedLeafCoordinates.contains(coordinateKey(candidate)))
                .toList();
        for (RavengardDungeonConfig.DungeonObject candidate : orphanCandidates) {
                if (consumedLeaves.contains(markerKey(candidate))) continue;
                if (occupiedLeafCoordinates.contains(coordinateKey(candidate))) continue;
                if (candidate.category() == null && !synthesizedOrphanLeaves.contains(markerKey(candidate))) {
                    RavengardDungeonConfig.DungeonObject counterpart = findGeneratedOrphanCounterpart(
                            orphanCandidates, candidate, consumedLeaves, synthesizedOrphanLeaves);
                    if (counterpart != null) {
                        synthesizedOrphanLeaves.add(markerKey(counterpart));
                        double midpointX = (candidate.x() + counterpart.x()) / 2.0;
                        double midpointZ = (candidate.z() + counterpart.z()) / 2.0;
                        boolean axisAlongX = Math.abs(candidate.x() - counterpart.x())
                                >= Math.abs(candidate.z() - counterpart.z());
                        RavengardDungeonConfig.DungeonObject syntheticTop =
                                new RavengardDungeonConfig.DungeonObject(
                                        "door", "door_1_top", midpointX, candidate.y(), midpointZ,
                                        axisAlongX ? 180f : 270f, candidate.pitch());
                        spawnedDoors.add(spawnDoorway(instance, List.of(candidate, counterpart), syntheticTop));
                        completeDoorways++;
                        topDisplays++;
                        continue;
                    }
                }
                if (synthesizedOrphanLeaves.contains(markerKey(candidate))) continue;
                spawnedDoors.add(spawnDoorway(instance, List.of(candidate), null));
                orphanLeaves++;
        }

        List<RavengardDungeonConfig.DungeonObject> ironGateMarkers = new ArrayList<>();
        for (RavengardDungeonConfig.DungeonObject object : objects) {
            if (!object.type().equals("iron_gate")) continue;
            inputMarkers++;
            ironGateMarkers.add(object);
        }
        ironGateMarkers.sort(DungeonDoor::compareIronGateMarkers);
        List<RavengardDungeonConfig.DungeonObject> uniqueIronGates = new ArrayList<>();
        for (RavengardDungeonConfig.DungeonObject object : ironGateMarkers) {
            int duplicateIndex = nearbyIronGateIndex(uniqueIronGates, object);
            if (duplicateIndex < 0) {
                uniqueIronGates.add(object);
            } else if (compareIronGateMarkers(object, uniqueIronGates.get(duplicateIndex)) < 0) {
                uniqueIronGates.set(duplicateIndex, object);
            }
        }
        List<List<RavengardDungeonConfig.DungeonObject>> ironGatePairs = pairIronGates(uniqueIronGates);
        for (List<RavengardDungeonConfig.DungeonObject> pair : ironGatePairs) {
            spawnedDoors.add(spawnIronGate(instance, pair));
        }
        linkOverlappingDoors(instance, spawnedDoors);

        int barrierReferences = spawnedDoors.stream()
                .mapToInt(DungeonDoor::retainedBarrierCount)
                .sum();
        int uniqueLeafMarkers = leavesByCoordinate.values().stream().mapToInt(List::size).sum();
        int leafDisplays = spawnedDoors.stream()
                .filter(door -> !door.verticalGate)
                .mapToInt(door -> door.leaves.size())
                .sum();
        return new SpawnSummary(inputMarkers, uniqueLeafMarkers, topsByCoordinate.size(),
                completeDoorways, partialDoorways, orphanLeaves,
                leafDisplays, topDisplays, uniqueIronGates.size(),
                spawnedDoors.size(), barrierReferences, barrierCellCount(instance));
    }

    private static RavengardDungeonConfig.DungeonObject findGeneratedOrphanCounterpart(
            List<RavengardDungeonConfig.DungeonObject> candidates,
            RavengardDungeonConfig.DungeonObject candidate,
            Set<String> consumedLeaves,
            Set<String> synthesizedLeaves) {
        RavengardDungeonConfig.DungeonObject best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RavengardDungeonConfig.DungeonObject other : candidates) {
            if (other == candidate || other.category() != null
                    || consumedLeaves.contains(markerKey(other))
                    || synthesizedLeaves.contains(markerKey(other))) continue;
            double dx = other.x() - candidate.x();
            double dz = other.z() - candidate.z();
            double distance = Math.hypot(dx, dz);
            if (Math.abs(distance - 2.0) > 0.08
                    || Math.abs(other.y() - candidate.y()) > 0.08) continue;
            if (distance < bestDistance) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void linkOverlappingDoors(Instance instance, List<DungeonDoor> doors) {
        List<Set<DungeonDoor>> groups = new ArrayList<>();
        for (DungeonDoor door : doors) {
            Set<DungeonDoor> group = new java.util.LinkedHashSet<>();
            group.add(door);
            for (Set<DungeonDoor> existing : groups) {
                if (sharesBarrier(group, existing)) group.addAll(existing);
            }
            groups.removeIf(existing -> sharesBarrier(group, existing));
            groups.add(group);
        }
        OVERLAP_GROUPS.put(instance, groups);
    }

    private static boolean sharesBarrier(Set<DungeonDoor> first, Set<DungeonDoor> second) {
        for (DungeonDoor left : first) {
            for (DungeonDoor right : second) {
                if (left == right) continue;
                for (var point : left.closedBarriers.keySet()) {
                    if (right.closedBarriers.containsKey(point)) return true;
                }
            }
        }
        return false;
    }

    private Set<DungeonDoor> overlapGroup() {
        for (Set<DungeonDoor> group : OVERLAP_GROUPS.getOrDefault(instance, List.of())) {
            if (group.contains(this)) return group;
        }
        return Set.of(this);
    }

    public record SpawnSummary(
            int inputMarkers,
            int uniqueLeafMarkers,
            int uniqueTopMarkers,
            int completeDoorways,
            int partialDoorways,
            int orphanLeaves,
            int leafDisplays,
            int topDisplays,
            int ironGateDisplays,
            int interactions,
            int barrierReferences,
            int uniqueBarrierCells) {
        public int displays() {
            return leafDisplays + topDisplays + ironGateDisplays;
        }
    }

    private static int nearbyIronGateIndex(
            List<RavengardDungeonConfig.DungeonObject> gates,
            RavengardDungeonConfig.DungeonObject candidate) {
        for (int index = 0; index < gates.size(); index++) {
            RavengardDungeonConfig.DungeonObject gate = gates.get(index);
            double dx = gate.x() - candidate.x();
            double dy = gate.y() - candidate.y();
            double dz = gate.z() - candidate.z();
            if (dx * dx + dy * dy + dz * dz > 0.1 * 0.1) continue;
            double yawDelta = Math.abs(normalizedYaw(gate.yaw()) - normalizedYaw(candidate.yaw()));
            yawDelta = Math.min(yawDelta, 360.0 - yawDelta);
            if (yawDelta <= 10.0) return index;
        }
        return -1;
    }

    private static int compareIronGateMarkers(
            RavengardDungeonConfig.DungeonObject first,
            RavengardDungeonConfig.DungeonObject second) {
        int comparison = Double.compare(ironGateGridDistance(first), ironGateGridDistance(second));
        if (comparison != 0) return comparison;
        comparison = Double.compare(first.x(), second.x());
        if (comparison != 0) return comparison;
        comparison = Double.compare(first.y(), second.y());
        if (comparison != 0) return comparison;
        comparison = Double.compare(first.z(), second.z());
        if (comparison != 0) return comparison;
        comparison = Float.compare(normalizedYaw(first.yaw()), normalizedYaw(second.yaw()));
        if (comparison != 0) return comparison;
        comparison = Float.compare(first.pitch(), second.pitch());
        if (comparison != 0) return comparison;
        comparison = first.category().compareTo(second.category());
        if (comparison != 0) return comparison;
        return first.type().compareTo(second.type());
    }

    private static double ironGateGridDistance(RavengardDungeonConfig.DungeonObject marker) {
        boolean axisAlongX = isAxisAlongX(marker.yaw());
        double radians = Math.toRadians(normalizedYaw(marker.yaw()));
        double facing = axisAlongX ? Math.cos(radians) : Math.sin(radians);
        double offset = facing >= 0.0 ? -IRON_GATE_ANCHOR_OFFSET : IRON_GATE_ANCHOR_OFFSET;
        double x = marker.x() + (axisAlongX ? offset : 0.0);
        double z = marker.z() + (axisAlongX ? 0.0 : offset);
        double dx = gridDistance(x);
        double dy = gridDistance(marker.y());
        double dz = gridDistance(z);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double gridDistance(double coordinate) {
        return Math.abs(coordinate - Math.rint(coordinate * 16.0) / 16.0);
    }

    private static String coordinateKey(RavengardDungeonConfig.DungeonObject object) {
        return coordinateKey(object.x(), object.y(), object.z());
    }

    private static String markerKey(RavengardDungeonConfig.DungeonObject object) {
        return coordinateKey(object) + ':' + String.format(
                java.util.Locale.ROOT, "%.3f", normalizedYaw(object.yaw()));
    }

    private static String coordinateKey(double x, double y, double z) {
        return String.format(java.util.Locale.ROOT, "%.3f:%.3f:%.3f", x, y, z);
    }

    private static boolean preferredLeaf(RavengardDungeonConfig.DungeonObject candidate,
                                         RavengardDungeonConfig.DungeonObject existing) {
        float candidateYaw = normalizedYaw(candidate.yaw());
        float existingYaw = normalizedYaw(existing.yaw());
        if (Math.abs(candidateYaw - existingYaw) < 0.001f) return false;
        return candidateYaw == 0f || candidateYaw == 90f
                || candidateYaw == 180f || candidateYaw == 270f;
    }

    static RavengardDungeonConfig.DungeonObject selectLeaf(
            List<RavengardDungeonConfig.DungeonObject> candidates, float expectedYaw) {
        return selectLeaf(candidates, expectedYaw, Set.of());
    }

    private static RavengardDungeonConfig.DungeonObject selectLeaf(
            List<RavengardDungeonConfig.DungeonObject> candidates, float expectedYaw,
            Set<String> consumedLeaves) {
        if (candidates == null || candidates.isEmpty()) return null;
        RavengardDungeonConfig.DungeonObject selected = null;
        double selectedDelta = Double.MAX_VALUE;
        for (RavengardDungeonConfig.DungeonObject candidate : candidates) {
            if (consumedLeaves.contains(markerKey(candidate))) continue;
            double delta = yawDelta(candidate.yaw(), expectedYaw);
            if (selected == null || delta < selectedDelta) {
                selected = candidate;
                selectedDelta = delta;
            } else if (Math.abs(delta - selectedDelta) < 0.001
                    && preferredLeaf(candidate, selected)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static DungeonDoor spawnDoorway(Instance instance,
                                            List<RavengardDungeonConfig.DungeonObject> cluster,
                                            RavengardDungeonConfig.DungeonObject top) {
        DungeonDoor door = new DungeonDoor(instance);
        double sumX = 0, sumY = 0, sumZ = 0;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (RavengardDungeonConfig.DungeonObject object : cluster) {
            minX = Math.min(minX, object.x()); maxX = Math.max(maxX, object.x());
            minZ = Math.min(minZ, object.z()); maxZ = Math.max(maxZ, object.z());
        }
        boolean axisAlongX = top != null ? isAxisAlongX(top.yaw())
                : cluster.size() == 1
                ? isAxisAlongX(cluster.get(0).yaw())
                : (maxX - minX) >= (maxZ - minZ);
        double midpoint = top != null
                ? (axisAlongX ? top.x() : top.z()) + 0.5
                : axisAlongX ? (minX + maxX) / 2.0 : (minZ + maxZ) / 2.0;
        int expectedLeafCount = top != null ? 2 : cluster.size();
        for (RavengardDungeonConfig.DungeonObject object : cluster) {
            double coordinate = axisAlongX ? object.x() : object.z();
            float closedYaw = woodenLeafYaw(
                    object.yaw(), axisAlongX, coordinate, midpoint, expectedLeafCount);
            Pos pos = woodenLeafPose(object, closedYaw);
            Entity display = door.spawnDisplay(pos, LEAF_MODEL, ItemDisplayMeta.DisplayContext.NONE);
            int swingSign = swingSign(closedYaw, axisAlongX, coordinate, midpoint, expectedLeafCount);
            Pos hinge = woodenHingePose(pos, axisAlongX, coordinate, midpoint, expectedLeafCount);
            door.leaves.add(new Leaf(display, pos, hinge.x(), hinge.z(), swingSign));
            sumX += object.x(); sumY += object.y(); sumZ += object.z();
        }
        double span = Math.max(maxX - minX, maxZ - minZ);
        float width = (float) Math.max(2.2, span + 1.6);
        Pos center = new Pos(sumX / cluster.size(), sumY / cluster.size() - 1.5, sumZ / cluster.size());
        door.interaction = door.spawnInteraction(center, width, 3.2f);
        if (top != null) {
            door.topDisplay = door.spawnDisplay(
                    woodenTopPose(top), TOP_MODEL,
                    ItemDisplayMeta.DisplayContext.NONE);
        }
        InteractableRegistry.register(door);
        for (Leaf leaf : door.leaves) {
            InteractableRegistry.registerExtraInteraction(door, leaf.display());
        }
        if (door.topDisplay != null) {
            InteractableRegistry.registerExtraInteraction(door, door.topDisplay);
        }
        // A single leaf or a top without its matching leaf is an incomplete
        // marker from the room catalogue.  It can still render and be
        // inspected, but must not create a full invisible doorway barrier.
        if (top != null && cluster.size() == 2) {
            door.prepareBarriers(axisAlongX, minX, maxX, minZ, maxZ, center.y());
        }
        return door;
    }

    static List<List<RavengardDungeonConfig.DungeonObject>> pairIronGates(
            List<RavengardDungeonConfig.DungeonObject> gates) {
        List<List<RavengardDungeonConfig.DungeonObject>> result = new ArrayList<>();
        boolean[] consumed = new boolean[gates.size()];
        for (int firstIndex = 0; firstIndex < gates.size(); firstIndex++) {
            if (consumed[firstIndex]) continue;
            RavengardDungeonConfig.DungeonObject first = gates.get(firstIndex);
            int bestIndex = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int secondIndex = firstIndex + 1; secondIndex < gates.size(); secondIndex++) {
                if (consumed[secondIndex]) continue;
                RavengardDungeonConfig.DungeonObject second = gates.get(secondIndex);
                double distance = ironGatePairDistance(first, second);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = secondIndex;
                }
            }
            consumed[firstIndex] = true;
            List<RavengardDungeonConfig.DungeonObject> pair = new ArrayList<>(2);
            pair.add(first);
            if (bestIndex >= 0) {
                consumed[bestIndex] = true;
                pair.add(gates.get(bestIndex));
            }
            result.add(List.copyOf(pair));
        }
        return List.copyOf(result);
    }

    private static double ironGatePairDistance(
            RavengardDungeonConfig.DungeonObject first,
            RavengardDungeonConfig.DungeonObject second) {
        if (Math.abs(first.y() - second.y()) > 0.1) return Double.MAX_VALUE;
        if (isAxisAlongX(first.yaw()) != isAxisAlongX(second.yaw())) return Double.MAX_VALUE;
        if (Math.abs(yawDelta(first.yaw(), second.yaw()) - 180.0) > 10.0) return Double.MAX_VALUE;
        boolean axisAlongX = isAxisAlongX(first.yaw());
        double parallel = axisAlongX
                ? Math.abs(first.x() - second.x())
                : Math.abs(first.z() - second.z());
        double perpendicular = axisAlongX
                ? Math.abs(first.z() - second.z())
                : Math.abs(first.x() - second.x());
        if (perpendicular > 0.1 || parallel < 2.0 || parallel > 5.0) return Double.MAX_VALUE;
        return parallel;
    }

    private static double yawDelta(float first, float second) {
        double delta = Math.abs(normalizedYaw(first) - normalizedYaw(second));
        return Math.min(delta, 360.0 - delta);
    }

    private static DungeonDoor spawnIronGate(Instance instance,
                                             List<RavengardDungeonConfig.DungeonObject> markers) {
        DungeonDoor gate = new DungeonDoor(instance, true);
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (RavengardDungeonConfig.DungeonObject marker : markers) {
            sumX += marker.x();
            sumY += marker.y();
            sumZ += marker.z();
            minX = Math.min(minX, marker.x());
            maxX = Math.max(maxX, marker.x());
            minZ = Math.min(minZ, marker.z());
            maxZ = Math.max(maxZ, marker.z());
        }

        boolean axisAlongX = isAxisAlongX(markers.getFirst().yaw());
        double midpointX = sumX / markers.size();
        double midpointY = sumY / markers.size();
        double midpointZ = sumZ / markers.size();
        double axisMidpoint = axisAlongX ? midpointX : midpointZ;
        for (RavengardDungeonConfig.DungeonObject marker : markers) {
            Pos closedPos = ironGateLeafPose(marker, axisAlongX, axisMidpoint, markers.size());
            Entity display = gate.spawnDisplay(closedPos, IRON_GATE_MODEL,
                    ItemDisplayMeta.DisplayContext.NONE);
            display.editEntityMeta(ItemDisplayMeta.class,
                    meta -> {
                        meta.setScale(IRON_GATE_SCALE);
                        meta.setTranslation(IRON_GATE_TRANSLATION);
                    });
            gate.leaves.add(new Leaf(display, closedPos, closedPos.x(), closedPos.z(), 0));
        }
        double span = axisAlongX ? maxX - minX : maxZ - minZ;
        float width = (float) Math.max(2.4, span + 1.5);
        Pos interactionCenter = new Pos(midpointX, midpointY - 1.5, midpointZ,
                normalizedYaw(markers.getFirst().yaw()), 0f);
        gate.interaction = gate.spawnInteraction(interactionCenter, width, 3.2f);
        InteractableRegistry.register(gate);
        for (Leaf leaf : gate.leaves) {
            InteractableRegistry.registerExtraInteraction(gate, leaf.display());
        }
        gate.prepareIronGateBarriers(axisAlongX, minX, maxX, minZ, maxZ,
                midpointX, midpointY, midpointZ);
        return gate;
    }

    @Override
    public boolean matchesVisibleTarget(Entity target) {
        if (target == topDisplay) return true;
        for (Leaf leaf : leaves) {
            if (leaf.display() == target) return true;
        }
        return false;
    }

    private static float normalizedYaw(float yaw) {
        return ((yaw % 360f) + 360f) % 360f;
    }

    static Pos woodenLeafPose(RavengardDungeonConfig.DungeonObject marker) {
        return new Pos(marker.x(), marker.y(), marker.z(),
                normalizedYaw(marker.yaw()), marker.pitch());
    }

    static Pos woodenLeafPose(RavengardDungeonConfig.DungeonObject marker, float yaw) {
        return new Pos(marker.x(), marker.y(), marker.z(), normalizedYaw(yaw), marker.pitch());
    }

    static Pos woodenTopPose(RavengardDungeonConfig.DungeonObject marker) {
        return new Pos(marker.x(), marker.y(), marker.z(),
                normalizedYaw(marker.yaw()), marker.pitch());
    }

    static Pos ironGatePose(RavengardDungeonConfig.DungeonObject marker) {
        return new Pos(marker.x(), marker.y(), marker.z(), normalizedYaw(marker.yaw()), marker.pitch());
    }

    static Pos ironGateLeafPose(RavengardDungeonConfig.DungeonObject marker) {
        return ironGatePose(marker);
    }

    static Pos ironGateLeafPose(RavengardDungeonConfig.DungeonObject marker,
                                boolean axisAlongX, double midpoint, int leafCount) {
        Pos pose = ironGatePose(marker);
        if (leafCount < 2) return pose;
        double coordinate = axisAlongX ? pose.x() : pose.z();
        double offset = coordinate < midpoint ? -IRON_GATE_ANCHOR_OFFSET
                : coordinate > midpoint ? IRON_GATE_ANCHOR_OFFSET : 0.0;
        return axisAlongX ? pose.withX(pose.x() + offset) : pose.withZ(pose.z() + offset);
    }

    static float woodenLeafYaw(float markerYaw, boolean axisAlongX, double coordinate,
                               double midpoint, int leafCount) {
        if (leafCount > 1 && Math.abs(coordinate - midpoint) > 0.001) {
            if (axisAlongX) return coordinate < midpoint ? 0f : 180f;
            return coordinate < midpoint ? 90f : 270f;
        }
        float normalized = normalizedYaw(markerYaw);
        float first = axisAlongX ? 0f : 90f;
        float second = axisAlongX ? 180f : 270f;
        double firstDelta = yawDelta(normalized, first);
        double secondDelta = yawDelta(normalized, second);
        if (Math.min(firstDelta, secondDelta) <= 10.0) {
            return firstDelta <= secondDelta ? first : second;
        }
        return firstDelta <= secondDelta ? first : second;
    }

    static Pos woodenHingePose(Pos closedPos, boolean axisAlongX, double coordinate,
                               double midpoint, int leafCount) {
        if (leafCount < 2 || Math.abs(coordinate - midpoint) < 0.001) {
            return closedPos;
        }
        return closedPos;
    }

    static Pos woodenLeafPoseAtFraction(Pos closedPos, double hingeX, double hingeZ,
                                        int swingSign, double fraction) {
        double radians = Math.toRadians(WOODEN_SWING_ANGLE * swingSign * fraction);
        double deltaX = closedPos.x() - hingeX;
        double deltaZ = closedPos.z() - hingeZ;
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double x = hingeX + deltaX * cosine - deltaZ * sine;
        double z = hingeZ + deltaX * sine + deltaZ * cosine;
        float yaw = (float) (closedPos.yaw() + WOODEN_SWING_ANGLE * swingSign * fraction);
        return new Pos(x, closedPos.y(), z, yaw, closedPos.pitch());
    }

    static int swingSign(float yaw, boolean axisAlongX, double coordinate,
                         double midpoint, int leafCount) {
        float normalized = normalizedYaw(yaw);
        double component = axisAlongX
                ? Math.cos(Math.toRadians(normalized))
                : Math.sin(Math.toRadians(normalized));
        if (Math.abs(component) > 0.5) return component > 0.0 ? 1 : -1;
        if (leafCount > 1) return coordinate < midpoint ? -1 : 1;
        return 1;
    }

    static boolean isAxisAlongX(float yaw) {
        float normalized = normalizedYaw(yaw);
        return normalized < 45f || normalized >= 135f && normalized < 225f
                || normalized >= 315f;
    }

    @Override
    public String castLabel() {
        return opened ? "Closing Door" : "Opening Door";
    }

    @Override
    public boolean allowReactivation() {
        return false;
    }

    @Override
    public boolean requiresLookingDuringChannel() {
        return false;
    }

    @Override
    public void channelStarted(Player player) {
        RavengardSounds.play(instance, focusPoint(), RavengardSounds.Cue.DOOR_CHANNEL);
    }

    @Override
    public void open(Player player) {
        if (swinging) return;
        boolean target = !opened;
        for (DungeonDoor door : overlapGroup()) {
            if (door.opened != target) door.swing(target);
        }
    }

    private void toggleBarriers(boolean open) {
        if (open) {
            for (var entry : closedBarriers.entrySet()) {
                if (entry.getValue()) releaseBarrier(instance, entry.getKey());
                entry.setValue(false);
            }
        } else {
            for (var entry : closedBarriers.entrySet()) {
                if (!entry.getValue() && retainBarrier(instance, entry.getKey())) {
                    entry.setValue(true);
                }
            }
        }
    }

    private void prepareBarriers(boolean axisAlongX, double minX, double maxX,
                                 double minZ, double maxZ, double baseY) {
        int crossX = (int) Math.floor((minX + maxX) / 2.0);
        int crossZ = (int) Math.floor((minZ + maxZ) / 2.0);
        int fromX = axisAlongX ? (int) Math.floor(minX - 0.5) : crossX;
        int toX = axisAlongX ? (int) Math.floor(maxX + 0.5) : crossX;
        int fromZ = axisAlongX ? crossZ : (int) Math.floor(minZ - 0.5);
        int toZ = axisAlongX ? crossZ : (int) Math.floor(maxZ + 0.5);
        for (int blockX = fromX; blockX <= toX; blockX++) {
            for (int blockZ = fromZ; blockZ <= toZ; blockZ++) {
                for (int blockY = (int) Math.floor(baseY); blockY <= (int) Math.floor(baseY) + 3; blockY++) {
                    var block = blockAtLoaded(instance, blockX, blockY, blockZ);
                    if (block == null) continue;
                    if (isDoorGapBlock(block)) {
                        var point = new net.minestom.server.coordinate.Vec(blockX, blockY, blockZ);
                        if (!closedBarriers.containsKey(point)) {
                            closedBarriers.put(point, retainBarrier(instance, point));
                        }
                    }
                }
            }
        }
    }

    private void prepareIronGateBarriers(boolean axisAlongX, double minX, double maxX,
                                         double minZ, double maxZ, double midpointX,
                                         double midpointY, double midpointZ) {
        int fromX = axisAlongX ? (int) Math.floor(minX) : (int) Math.floor(midpointX);
        int toX = axisAlongX ? (int) Math.floor(maxX) : fromX;
        int fromZ = axisAlongX ? (int) Math.floor(midpointZ) : (int) Math.floor(minZ);
        int toZ = axisAlongX ? fromZ : (int) Math.floor(maxZ);
        int fromY = (int) Math.floor(midpointY - IRON_GATE_LIFT);
        int toY = (int) Math.floor(midpointY);
        for (int blockX = fromX; blockX <= toX; blockX++) {
            for (int blockZ = fromZ; blockZ <= toZ; blockZ++) {
                for (int blockY = fromY; blockY <= toY; blockY++) {
                    var block = blockAtLoaded(instance, blockX, blockY, blockZ);
                    if (block == null || !isDoorGapBlock(block)) continue;
                    var point = new net.minestom.server.coordinate.Vec(blockX, blockY, blockZ);
                    if (!closedBarriers.containsKey(point)) {
                        closedBarriers.put(point, retainBarrier(instance, point));
                    }
                }
            }
        }
    }

    static long barrierKey(net.minestom.server.coordinate.Point point) {
        return ((long) point.blockX() & 0x1FFFFFL) << 42
                | ((long) point.blockY() & 0x3FFFFFL) << 21
                | ((long) point.blockZ() & 0x1FFFFFL);
    }

    private static boolean isDoorGapBlock(net.minestom.server.instance.block.Block block) {
        return block == net.minestom.server.instance.block.Block.AIR
                || block == net.minestom.server.instance.block.Block.BARRIER;
    }

    static boolean retainBarrier(Instance instance,
                                 net.minestom.server.coordinate.Point point) {
        synchronized (BARRIERS) {
            Map<Long, BarrierState> states = BARRIERS.computeIfAbsent(instance, ignored -> new HashMap<>());
            long key = barrierKey(point);
            BarrierState current = states.get(key);
            if (current == null) {
                var original = blockAtLoaded(instance, point.blockX(), point.blockY(), point.blockZ());
                if (original == null) {
                    if (states.isEmpty()) BARRIERS.remove(instance);
                    return false;
                }
                states.put(key, new BarrierState(original, 1));
                if (original != net.minestom.server.instance.block.Block.BARRIER) {
                    instance.setBlock(point.blockX(), point.blockY(), point.blockZ(),
                            net.minestom.server.instance.block.Block.BARRIER);
                }
            } else {
                states.put(key, new BarrierState(current.original(), current.references() + 1));
                if (instance.getBlock(point.blockX(), point.blockY(), point.blockZ())
                        != net.minestom.server.instance.block.Block.BARRIER) {
                    instance.setBlock(point.blockX(), point.blockY(), point.blockZ(),
                            net.minestom.server.instance.block.Block.BARRIER);
                }
            }
            return true;
        }
    }

    private int retainedBarrierCount() {
        return (int) closedBarriers.values().stream().filter(Boolean::booleanValue).count();
    }

    static int barrierCellCount(Instance instance) {
        synchronized (BARRIERS) {
            Map<Long, BarrierState> states = BARRIERS.get(instance);
            return states == null ? 0 : states.size();
        }
    }

    static boolean isBarrierManaged(Instance instance,
                                    net.minestom.server.coordinate.Point point) {
        synchronized (BARRIERS) {
            Map<Long, BarrierState> states = BARRIERS.get(instance);
            return states != null && states.containsKey(barrierKey(point));
        }
    }

    static void releaseBarrier(Instance instance,
                               net.minestom.server.coordinate.Point point) {
        synchronized (BARRIERS) {
            Map<Long, BarrierState> states = BARRIERS.get(instance);
            if (states == null) return;
            long key = barrierKey(point);
            BarrierState current = states.get(key);
            if (current == null) return;
            if (current.references() > 1) {
                states.put(key, new BarrierState(current.original(), current.references() - 1));
                return;
            }
            states.remove(key);
            if (instance.getBlock(point.blockX(), point.blockY(), point.blockZ())
                    == net.minestom.server.instance.block.Block.BARRIER) {
                var restored = current.original() == net.minestom.server.instance.block.Block.BARRIER
                        ? net.minestom.server.instance.block.Block.AIR
                        : current.original();
                instance.setBlock(point.blockX(), point.blockY(), point.blockZ(), restored);
            }
            if (states.isEmpty()) BARRIERS.remove(instance);
        }
    }

    private static net.minestom.server.instance.block.Block blockAtLoaded(
            Instance instance, int blockX, int blockY, int blockZ) {
        try {
            return instance.getBlock(blockX, blockY, blockZ);
        } catch (RuntimeException unloaded) {
            if (!instance.isRegistered()) return null;
            try {
                instance.loadChunk(blockX >> 4, blockZ >> 4).join();
                return instance.getBlock(blockX, blockY, blockZ);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    @Override
    public void remove() {
        if (removed) return;
        super.remove();
        leaves.forEach(leaf -> {
            if (!leaf.display().isRemoved()) leaf.display().remove();
        });
        if (topDisplay != null && !topDisplay.isRemoved()) topDisplay.remove();
        for (var entry : closedBarriers.entrySet()) {
            if (entry.getValue()) releaseBarrier(instance, entry.getKey());
        }
        closedBarriers.clear();
        List<Set<DungeonDoor>> groups = OVERLAP_GROUPS.get(instance);
        if (groups != null) {
            groups.removeIf(group -> {
                group.remove(this);
                return group.isEmpty();
            });
            if (groups.isEmpty()) OVERLAP_GROUPS.remove(instance);
        }
    }

    private void swing(boolean open) {
        if (removed) return;
        opened = open;
        swinging = true;
        RavengardSounds.play(instance, focusPoint(),
                open ? RavengardSounds.Cue.DOOR_OPEN : RavengardSounds.Cue.DOOR_CLOSE);
        toggleBarriers(open);
        for (int step = 1; step <= SWING_TICKS; step++) {
            int tick = step;
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                if (removed) {
                    swinging = false;
                    return;
                }
                double fraction = open ? (double) tick / SWING_TICKS
                        : (double) (SWING_TICKS - tick) / SWING_TICKS;
                for (Leaf leaf : leaves) {
                    if (!isActive(leaf.display())) continue;
                    if (verticalGate) {
                        leaf.display().teleport(ironGatePoseAtFraction(leaf.closedPos(), fraction));
                    } else {
                        leaf.display().teleport(woodenLeafPoseAtFraction(
                                leaf.closedPos(), leaf.hingeX(), leaf.hingeZ(),
                                leaf.swingSign(), fraction));
                    }
                }
                if (tick == SWING_TICKS) {
                    swinging = false;
                }
            }).delay(TaskSchedule.tick(step)).schedule();
        }
    }

    static Pos ironGatePoseAtFraction(Pos closedPos, double fraction) {
        return closedPos.withY(closedPos.y() + IRON_GATE_LIFT * fraction);
    }

    private static final class StaticPiece extends DungeonInteractable {
        private StaticPiece(Instance instance) {
            super(instance);
        }

        @Override
        public void open(Player player) {
        }

        private void spawnTop(Pos position) {
            spawnDisplay(position, TOP_MODEL, ItemDisplayMeta.DisplayContext.NONE);
        }
    }
}
