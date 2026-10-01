package net.swofty.type.ravengardgeneric.entity.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip;
import net.swofty.type.ravengardgeneric.sound.RavengardSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class RavengardMob extends net.minestom.server.entity.EntityCreature {
    private static final List<RavengardMob> MOBS = new CopyOnWriteArrayList<>();
    private static final Set<java.util.UUID> REPORTED_TICK_FAILURES = ConcurrentHashMap.newKeySet();
    private static final TextColor BAR_FULL = TextColor.color(0x5FEC7B);
    private static final TextColor BAR_EMPTY = TextColor.color(0x3D3D3D);
    private static final int BAR_SEGMENTS = 10;
    private static final float PLAYER_DAMAGE_PER_HIT = 2f;
    private static final int ATTACK_COOLDOWN_TICKS = 30;
    private static final int FLASH_TICKS = 9;
    private static final int FLASH_COLOR = 0xFF6666;
    private static final int BASE_COLOR = 0xFFFFFF;
    private static final double VISUAL_GROUND_LIFT = 0.125;
    private static final Set<String> BOTTLE_MIMIC_PARTS =
            Set.of("bottom", "eye", "tongue", "top", "topcap");
    private static final Map<String, Set<String>> APPEARANCE_PARTS = Map.ofEntries(
            Map.entry("skeleton_archer_elite", Set.of("arrow", "arrowtip", "arrowtip2", "body", "head",
                    "leftarm", "leftleg", "quiver", "rightarm", "rightleg")),
            Map.entry("skeleton_knight_elite", Set.of("body", "head", "leftarm", "leftleg", "rightarm",
                    "rightleg", "shield")),
            Map.entry("skeleton_warrior_elite", Set.of("armleft", "armright", "body", "head", "legleft",
                    "legright")),
            Map.entry("zombie_knight_elite", Set.of("body", "head", "helm", "leftarm", "leftleg",
                    "rightarm", "rightleg")),
            Map.entry("demon_grunt_elite", Set.of("belt", "body", "clothb", "clothf", "ear", "ear2",
                    "head", "hornl", "hornr", "leftarm", "leftleg", "rightarm", "rightleg",
                    "wingl", "wingr")),
            Map.entry("demon_imp_elite", Set.of("arml", "armr", "body", "ear", "ear2", "head", "hornl",
                    "hornr", "legl", "legr", "wingl", "wingr")),
            Map.entry("oculus_elite", Set.of("eye", "eye_beam", "eye_dead", "eye_purple", "hornr", "hornr2",
                    "tail", "tentacle", "wingl", "wingr", "wingtipl", "wingtipr")),
            Map.entry("bottle_mimic_short_green", BOTTLE_MIMIC_PARTS),
            Map.entry("bottle_mimic_short_red", BOTTLE_MIMIC_PARTS),
            Map.entry("bottle_mimic_tall_aqua", BOTTLE_MIMIC_PARTS),
            Map.entry("bottle_mimic_tall_green", BOTTLE_MIMIC_PARTS),
            Map.entry("bottle_mimic_tall_pink", BOTTLE_MIMIC_PARTS),
            Map.entry("normal_mimic", Set.of("bottom", "tongue", "top")));
    private static final Map<String, Map<String, String>> APPEARANCE_PART_ALIASES = Map.of(
            "skeleton_warrior_elite", Map.of(
                    "armleft", "leftarm",
                    "armright", "rightarm",
                    "legleft", "leftleg",
                    "legright", "rightleg"));
    // a wooden mimic has no eyes: those two bones of the silver mimic's rig carry nothing
    private static final Map<String, Set<String>> APPEARANCE_HIDDEN_PARTS = Map.of(
            "normal_mimic", Set.of("eye_purple", "eyesocket"));
    /**
     * Model-space lift in pixels for parts whose model is drawn around a different pivot than the
     * one the clip was captured with. Each value is the vertical distance between the two models'
     * otherwise identical boxes in the pack.
     */
    private static final Map<String, Map<String, Float>> APPEARANCE_PART_LIFT = Map.of(
            "skeleton_archer", Map.of("body", -9f),
            "skeleton_knight_elite", Map.of("body", 9f, "rightarm", -1f),
            "skeleton_warrior_elite", Map.of("body", 11f),
            "zombie_knight_elite", Map.of("body", 4f));
    /**
     * Mimics were captured with the same carrier offset as the humanoids but are not a humanoid tall:
     * this is how far their resting pose sits above the floor with that offset.
     */
    private static final Map<String, Double> CLIP_GROUND_DROP = Map.of(
            "silver_mimic", 1.28125,
            "bottle_mimic_short_yellow", 1.75,
            "bottle_mimic_tall_green", 1.75);
    // the pack turns the demons' ears outwards in the model's head display transform, which an
    // item display in the NONE context does not apply
    private static final Map<String, Float> DEMON_EAR_YAW = Map.of("ear", -47.5f, "ear2", 47.5f);
    // captured lying dormant on the ground; once woken they stay on their feet
    private static final Set<String> DORMANT_IDLE_CLIPS = Set.of("zombie_knight");
    private static final float CASTER_DAMAGE_PER_HIT = 1.5f;
    private static final double CASTER_RANGE = 11.0;
    private static final int CASTER_COOLDOWN_TICKS = 45;
    private static final double KNOCKBACK_HORIZONTAL = 0.79 * 20;
    private static final double KNOCKBACK_VERTICAL = 0.50 * 20;
    // the invisible zombie body is the combat target: scaling it keeps right-clicks passing through
    // to the held item, which an interaction entity would swallow
    static final double COMBAT_SCALE = 1.15;
    static final double COMBAT_HITBOX_WIDTH = 0.6 * COMBAT_SCALE;
    static final double COMBAT_HITBOX_HEIGHT = 1.95 * COMBAT_SCALE;

    private final RavengardMobClip clip;
    private final String appearanceType;
    private final Consumer<RavengardMob> onDefeated;
    private final boolean stationaryCaster;
    private final List<Entity> parts = new ArrayList<>();
    private final List<Vec> partOffsets = new ArrayList<>();
    private final List<String> partModels = new ArrayList<>();
    private LivingEntity healthBar;
    private Instance instance;

    private Pos position;
    private double health;
    private final double maxHealth;
    private String currentAnimation = "idle";
    private boolean oneShot;
    private int frame;
    private float[][] lastSentRotation;
    private float[][] localRotation;
    private float[][] localTranslation;
    private int[] partDuration;
    private Vec[] partLift;
    private boolean[] scaleChanged;
    private boolean restoreScales;
    private double groundDrop;
    private float lastAppliedYaw = Float.NaN;
    private int attackCooldown;
    private int flashTicksLeft;
    private boolean dying;
    private boolean attackReported;
    private boolean deathReported;
    private volatile boolean rigRemoved;
    private float rigYaw;
    private static final float MAX_TURN_PER_TICK = 25f;

    /**
     * The captured knight charge: a sprint at roughly three times walk speed for a handful of
     * ticks that always ends in the purple slash. Range and cooldown are not observable.
     */
    private String chargeAnimation() {
        return clip.animation("charge") != null ? "charge" : "attack_purple";
    }
    private static final double CHARGE_MIN_RANGE = 3.0;
    private static final double CHARGE_MAX_RANGE = 7.0;
    private static final double CHARGE_HIT_RANGE = 2.4;
    private static final double MELEE_STANDOFF = 1.2;
    private static final double CHARGE_SPEED_MULTIPLIER = 3.0;
    private static final int CHARGE_TICKS = 8;
    private static final int CHARGE_COOLDOWN_TICKS = 100;
    private int chargeTicksLeft;
    private int chargeCooldown;

    public RavengardMob(RavengardMobClip clip, Pos position) {
        this(clip, position, clip.name(), ignored -> {});
    }

    public RavengardMob(RavengardMobClip clip, Pos position, String appearanceType) {
        this(clip, position, appearanceType, ignored -> {});
    }

    public RavengardMob(RavengardMobClip clip, Pos position, Consumer<RavengardMob> onDefeated) {
        this(clip, position, clip.name(), onDefeated);
    }

    public RavengardMob(RavengardMobClip clip, Pos position, String appearanceType,
                        Consumer<RavengardMob> onDefeated) {
        super(EntityType.ZOMBIE);
        this.clip = clip;
        this.appearanceType = APPEARANCE_PARTS.containsKey(appearanceType)
                ? appearanceType : clip.name();
        this.onDefeated = onDefeated;
        this.stationaryCaster = "demon_imp".equals(clip.name());
        this.position = position;
        this.rigYaw = position.yaw();
        if (clip.animation("idle") == null && clip.animation("walk") != null) {
            this.currentAnimation = "walk";
        }
        this.maxHealth = clip.health() <= 0 ? 100 : clip.health();
        this.health = maxHealth;
        setInvisible(true);
        setSilent(true);
        setAutoViewable(true);
        getAttribute(net.minestom.server.entity.attribute.Attribute.SCALE).setBaseValue(COMBAT_SCALE);
        setBoundingBox(COMBAT_HITBOX_WIDTH, COMBAT_HITBOX_HEIGHT, COMBAT_HITBOX_WIDTH);
        getAttribute(net.minestom.server.entity.attribute.Attribute.MOVEMENT_SPEED)
                .setBaseValue(Math.max(0.15, clip.walkSpeed() * 2));
        getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH)
                .setBaseValue((float) maxHealth);
        setHealth((float) maxHealth);
        RavengardMobClip.Animation death = clip.animation("death");
        if (death != null) {
            setRemovalAnimationDelay(Math.max(getRemovalAnimationDelay(), death.frames().size() * 50 + 100));
        }
        if (clip.walkSpeed() > 0) {
            // Minestom's follower only counts a node as reached at the node's own height and cannot
            // step onto a slab without a jump node; path nodes sit a block above the slabs and plants
            // a mob really stands in, which left it pacing in front of them
            getNavigator().setNodeFollower(() ->
                    new net.swofty.type.generic.entity.ai.vanilla.VanillaGroundNodeFollower(this));
            addAIGroup(
                    List.of(new net.minestom.server.entity.ai.goal.MeleeAttackGoal(this, 1.8,
                            java.time.Duration.ofMillis(1500))),
                    List.of(targetable(new net.minestom.server.entity.ai.target.LastEntityDamagerTarget(this, 16)),
                             new net.minestom.server.entity.ai.target.ClosestEntityTarget(this, 14,
                                     RavengardMob::isTargetable)));
        }
    }

    private net.minestom.server.entity.ai.TargetSelector targetable(
            net.minestom.server.entity.ai.TargetSelector selector) {
        return new net.minestom.server.entity.ai.TargetSelector(this) {
            @Override
            public Entity findTarget() {
                Entity found = selector.findTarget();
                return isTargetable(found) ? found : null;
            }
        };
    }

    public String displayName() {
        StringBuilder name = new StringBuilder();
        for (String word : appearanceType.split("_")) {
            if (word.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    @Override
    public void attack(Entity target, boolean swingHand) {
        if (dying || attackCooldown > 0 || chargeTicksLeft > 0) return;
        if (target instanceof Player && !isTargetable(target)) return;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        List<String> options = new ArrayList<>(clip.attackAnimations());
        options.remove(chargeAnimation());
        if (!options.isEmpty()) {
            startOneShot(options.get(ThreadLocalRandom.current().nextInt(options.size())));
        }
        hit(target);
    }

    private static final float SHIELD_BLOCK_KEPT = 0.3f;

    private void hit(Entity target) {
        if (target instanceof LivingEntity living) {
            float damage = playerDamage();
            if (living instanceof net.swofty.type.ravengardgeneric.user.RavengardPlayer player
                    && player.isBlockingWithShield()) {
                damage *= SHIELD_BLOCK_KEPT;
                player.playSound(net.kyori.adventure.sound.Sound.sound()
                        .type(net.kyori.adventure.key.Key.key("item.shield.block"))
                        .volume(1.0f)
                        .pitch(1.0f)
                        .build());
            }
            float before = living.getHealth();
            playAttackSound();
            living.damage(net.minestom.server.entity.damage.Damage.fromEntity(this, damage));
            if (living instanceof net.swofty.type.ravengardgeneric.user.RavengardPlayer player) {
                net.swofty.type.ravengardgeneric.classes.RavengardAbilityService.breakShadows(player);
            }
            if (!attackReported) {
                attackReported = true;
                org.tinylog.Logger.info(
                        "Ravengard mob attack: type={} entity={} target={} distance={} targetHealth={} -> {}",
                        clip.name(), getEntityId(), target.getEntityId(), position.distance(target.getPosition()),
                        before, living.getHealth());
            }
            Vec away = living.getPosition().sub(position).asVec().withY(0);
            if (away.lengthSquared() > 1e-6) {
                away = away.normalize();
                living.setVelocity(new Vec(away.x() * KNOCKBACK_HORIZONTAL, KNOCKBACK_VERTICAL,
                        away.z() * KNOCKBACK_HORIZONTAL));
            }
        }
    }

    // the melee goal keeps its path once in reach, which would walk the mob into the player
    private void holdMeleeStandoff() {
        Entity target = getTarget();
        if (dying || target == null || getNavigator().getGoalPosition() == null) return;
        if (position.distanceSquared(target.getPosition()) <= MELEE_STANDOFF * MELEE_STANDOFF) {
            getNavigator().setPathTo(null);
        }
    }

    private void tickCharge() {
        if (dying || clip.animation(chargeAnimation()) == null) return;
        if (chargeCooldown > 0) chargeCooldown--;
        Entity target = getTarget();
        var speed = getAttribute(net.minestom.server.entity.attribute.Attribute.MOVEMENT_SPEED);
        double base = Math.max(0.15, clip.walkSpeed() * 2);
        if (chargeTicksLeft > 0) {
            chargeTicksLeft--;
            boolean arrived = target != null && !target.isRemoved()
                    && position.distance(target.getPosition()) <= CHARGE_HIT_RANGE;
            if (arrived || chargeTicksLeft == 0) {
                chargeTicksLeft = 0;
                speed.setBaseValue(base);
                startOneShot(chargeAnimation());
                attackCooldown = ATTACK_COOLDOWN_TICKS;
                if (arrived) {
                    hit(target);
                }
            }
            return;
        }
        if (target == null || target.isRemoved() || attackCooldown > 0 || chargeCooldown > 0) return;
        double distance = position.distance(target.getPosition());
        if (distance >= CHARGE_MIN_RANGE && distance <= CHARGE_MAX_RANGE) {
            chargeTicksLeft = CHARGE_TICKS;
            chargeCooldown = CHARGE_COOLDOWN_TICKS;
            speed.setBaseValue(base * CHARGE_SPEED_MULTIPLIER);
        }
    }

    public boolean isDying() {
        return dying;
    }

    @Override
    public boolean damage(net.minestom.server.entity.damage.Damage damage) {
        if (dying || rigRemoved) return false;
        boolean applied = super.damage(damage);
        if (!applied) return false;
        health = getHealth();
        updateBar();
        if (isDead()) {
            beginDeath();
        } else {
            flash();
            RavengardSounds.play(instance, position, RavengardSounds.Cue.MOB_HURT);
        }
        return true;
    }

    private void beginDeath() {
        dying = true;
        RavengardSounds.play(instance, position, RavengardSounds.Cue.MOB_DEATH);
        if (!deathReported) {
            deathReported = true;
            org.tinylog.Logger.info("Ravengard mob defeated: type={} entity={} pos={}",
                    clip.name(), getEntityId(), position);
        }
        getAIGroups().clear();
        getNavigator().setPathTo(null);
        try {
            onDefeated.accept(this);
        } catch (RuntimeException exception) {
            org.tinylog.Logger.warn("Ravengard mob defeat callback failed: type={} entity={} error={}",
                    clip.name(), getEntityId(), exception.getMessage());
        }
        if (clip.animation("death") != null && !clip.animation("death").frames().isEmpty()) {
            startOneShot("death");
        } else {
            removeRig();
        }
    }

    private void startOneShot(String animation) {
        if (clip.animation(animation) == null) return;
        currentAnimation = animation;
        oneShot = true;
        frame = 0;
        restoreScales = true;
    }

    private void flash() {
        flashTicksLeft = FLASH_TICKS;
        retint(FLASH_COLOR);
    }

    private void retint(int rgb) {
        for (int i = 0; i < parts.size() && i < partModels.size(); i++) {
            final int index = i;
            parts.get(index).editEntityMeta(ItemDisplayMeta.class, meta ->
                    meta.setItemStack(partItem(partModels.get(index), rgb)));
        }
    }

    private ItemStack partItem(RavengardMobClip.Part part, int rgb) {
        return partItem(appearanceModel(appearanceType, clip.name(), part.name(), part.model()), rgb);
    }

    private ItemStack partItem(String model, int rgb) {
        if (model == null) return ItemStack.AIR;
        return ItemStack.builder(Material.LEATHER_BOOTS)
                .set(DataComponents.ITEM_MODEL, model)
                .set(DataComponents.DYED_COLOR, new Color(rgb))
                .build();
    }

    static String appearanceModel(String appearanceType, String baseType, String partName,
                                  String baseModel) {
        if (APPEARANCE_HIDDEN_PARTS.getOrDefault(appearanceType, Set.of()).contains(partName)) return null;
        Set<String> parts = APPEARANCE_PARTS.get(appearanceType);
        if (parts == null || !parts.contains(partName)) return baseModel;
        String basePath = ":entity/" + baseType + "/";
        int start = baseModel.indexOf(basePath);
        if (start < 0) return baseModel;
        String modelPart = APPEARANCE_PART_ALIASES.getOrDefault(appearanceType, Map.of())
                .getOrDefault(partName, partName);
        String modelSuffix = baseModel.substring(start + basePath.length());
        if (!modelPart.equals(partName) && modelSuffix.equals(partName)) {
            modelSuffix = modelPart;
        }
        return baseModel.substring(0, start) + ":entity/" + appearanceType + "/"
                + modelSuffix;
    }

    public String appearanceType() {
        return appearanceType;
    }

    public static List<RavengardMob> mobs() {
        return MOBS;
    }

    public static RavengardMob combatTarget(Entity entity) {
        if (entity instanceof RavengardMob mob) {
            return mob;
        }
        if (entity != null) {
            for (RavengardMob mob : MOBS) {
                if (mob.ownsCombatEntity(entity)) {
                    return mob;
                }
            }
        }
        return null;
    }

    private boolean ownsCombatEntity(Entity entity) {
        return entity == this || entity == healthBar || parts.contains(entity);
    }

    public static void removeInstance(Instance target) {
        if (target == null) return;
        MOBS.stream()
                .filter(mob -> mob.getInstance() == target)
                .toList()
                .forEach(RavengardMob::removeRig);
    }

    public void spawnMob(Instance instance) {
        this.instance = instance;
        int partCount = clip.parts().size();
        this.groundDrop = CLIP_GROUND_DROP.getOrDefault(clip.name(), 0.0);
        this.lastSentRotation = new float[partCount][];
        this.partLift = new Vec[partCount];
        this.scaleChanged = new boolean[partCount];
        this.partDuration = new int[partCount];
        this.localRotation = new float[partCount][];
        this.localTranslation = new float[partCount][];
        for (int i = 0; i < partCount; i++) {
            RavengardMobClip.Part part = clip.parts().get(i);
            localRotation[i] = part.leftRotation() != null
                    ? part.leftRotation().clone() : new float[]{0, 0, 0, 1};
            localTranslation[i] = part.translation() != null
                    ? part.translation().clone() : new float[]{0, 0, 0};
        }
        setInstance(instance, position);
        for (int partIndex = 0; partIndex < partCount; partIndex++) {
            RavengardMobClip.Part part = clip.parts().get(partIndex);
            Entity display = new Entity(EntityType.ITEM_DISPLAY);
            display.setNoGravity(true);
            display.setAutoViewable(true);
            display.editEntityMeta(ItemDisplayMeta.class, meta -> {
                meta.setItemStack(partItem(part, BASE_COLOR));
                meta.setDisplayContext(displayContext(part.context()));
                if (part.translation() != null) meta.setTranslation(vec(part.translation()));
                meta.setScale(vec(part.scale()));
                if (part.leftRotation() != null) meta.setLeftRotation(part.leftRotation());
                Float earYaw = clip.name().startsWith("demon_") ? DEMON_EAR_YAW.get(part.name()) : null;
                meta.setRightRotation(earYaw == null ? part.rightRotation() : yawRotation(earYaw));
                meta.setTransformationInterpolationDuration(1);
                meta.setPosRotInterpolationDuration(2);
                meta.setViewRange(part.viewRange());
                meta.setBrightness(15, 15);
            });
            double[] off = part.offset();
            Vec offset = new Vec(off[0], off[1] + VISUAL_GROUND_LIFT - groundDrop, off[2]);
            display.setInstance(instance, position.add(offset).withView(0, 0));
            parts.add(display);
            partOffsets.add(offset);
            partModels.add(appearanceModel(appearanceType, clip.name(), part.name(), part.model()));
            Float lift = APPEARANCE_PART_LIFT.getOrDefault(appearanceType, Map.of()).get(part.name());
            partLift[partIndex] = lift == null ? null : new Vec(0, lift / 16.0 * part.scale()[1], 0);
        }
        java.util.Arrays.fill(partDuration, 1);

        healthBar = new LivingEntity(EntityType.TEXT_DISPLAY);
        healthBar.setNoGravity(true);
        healthBar.setAutoViewable(true);
        healthBar.editEntityMeta(TextDisplayMeta.class, meta -> {
            meta.setText(barText());
            meta.setScale(new Vec(0.3, 0.3, 0.3));
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
            meta.setBackgroundColor(0);
            meta.setHasNoGravity(true);
        });
        healthBar.setInstance(instance, position.add(0, barHeight(), 0));

        MOBS.add(this);
        int variantModelParts = (int) partModels.stream().filter(java.util.Objects::nonNull)
                .filter(model -> model.contains(":entity/" + appearanceType + "/"))
                .count();
        org.tinylog.Logger.debug(
                "Spawned Ravengard mob rig: mobType={} animationClipType={} appearance={} pos={} "
                        + "displayParts={} variantModelParts={}",
                appearanceType, clip.name(), appearanceType, position, parts.size(), variantModelParts);
    }

    private String restingAnimation(boolean moved) {
        boolean awake = getTarget() != null && DORMANT_IDLE_CLIPS.contains(clip.name());
        return (moved || awake) && clip.animation("walk") != null ? "walk" : "idle";
    }

    private double barHeight() {
        return 2.25 - groundDrop * 0.6;
    }

    private static float[] yawRotation(float degrees) {
        double half = Math.toRadians(degrees) / 2;
        return new float[]{0, (float) Math.sin(half), 0, (float) Math.cos(half)};
    }

    private static ItemDisplayMeta.DisplayContext displayContext(int contextOrdinal) {
        ItemDisplayMeta.DisplayContext[] contexts = ItemDisplayMeta.DisplayContext.values();
        if (contextOrdinal < 0 || contextOrdinal >= contexts.length) {
            return ItemDisplayMeta.DisplayContext.NONE;
        }
        // Captured Ravengard rigs already carry their complete transform in the display
        // metadata. HEAD applies the vanilla helmet transform a second time and can move
        // the custom item model outside the camera frustum on 26.2 clients.
        if (contexts[contextOrdinal] == ItemDisplayMeta.DisplayContext.HEAD) {
            return ItemDisplayMeta.DisplayContext.NONE;
        }
        return contexts[contextOrdinal];
    }

    private void updateBar() {
        if (isRigEntityActive(healthBar)) {
            healthBar.editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(barText()));
        }
    }

    private Component barText() {
        int full = (int) Math.ceil(BAR_SEGMENTS * Math.max(0, health) / maxHealth);
        return Component.text("■".repeat(full)).color(BAR_FULL)
                .append(Component.text("■".repeat(BAR_SEGMENTS - full)).color(BAR_EMPTY));
    }

    public void removeRig() {
        if (rigRemoved) return;
        rigRemoved = true;
        MOBS.remove(this);
        REPORTED_TICK_FAILURES.remove(getUuid());
        parts.forEach(part -> {
            if (!part.isRemoved()) part.remove();
        });
        if (healthBar != null && !healthBar.isRemoved()) healthBar.remove();
        if (!isRemoved()) remove();
    }

    public void tickRig() {
        if (rigRemoved) {
            return;
        }
        if (instance == null || isRemoved() || getInstance() != instance) {
            removeRig();
            return;
        }
        Pos previous = position;
        position = getPosition();
        boolean moved = !previous.samePoint(position);
        float yaw = updateRigYaw(previous, moved);

        if (attackCooldown > 0) attackCooldown--;
        if (flashTicksLeft > 0 && --flashTicksLeft == 0) {
            retint(BASE_COLOR);
        }
        updateTarget();
        holdMeleeStandoff();
        tickStationaryCaster();
        tickCharge();

        RavengardMobClip.Animation animation = clip.animation(currentAnimation);
        if (!oneShot) {
            String desired = restingAnimation(moved);
            if (!desired.equals(currentAnimation)) {
                currentAnimation = desired;
                frame = 0;
                restoreScales = true;
                animation = clip.animation(desired);
            }
        }
        if (animation == null || animation.frames().isEmpty()) {
            return;
        }
        if (frame >= animation.frames().size()) {
            if (dying) {
                removeRig();
                return;
            } else if (animation.loop()) {
                frame = 0;
            } else {
                oneShot = false;
                currentAnimation = restingAnimation(moved);
                frame = 0;
                restoreScales = true;
                animation = clip.animation(currentAnimation);
                if (animation == null || animation.frames().isEmpty()) return;
            }
        }

        // parts only receive keyframes when the capture animated them, so a facing change
        // re-sends every part's current pose composed with the new yaw or the rig turns piecemeal
        boolean turned = Float.isNaN(lastAppliedYaw)
                || Math.abs(wrapDegrees(yaw - lastAppliedYaw)) > 0.25f;
        lastAppliedYaw = yaw;

        Map<String, RavengardMobClip.Keyframe> keyframes = animation.frames().get(frame);
        // clips face +Z, which is yaw 0; Minecraft yaw turns the other way round the Y axis
        final float turn = -yaw;
        for (int i = 0; i < parts.size() && i < partModels.size(); i++) {
            Entity part = parts.get(i);
            if (!isRigEntityActive(part)) continue;
            if ((moved || oneShot) && !dying) {
                // the display's own yaw would turn the model a second time
                part.teleport(position.add(partOffsets.get(i)).withView(0, 0));
            }
            RavengardMobClip.Keyframe keyframe = keyframes.get(String.valueOf(i));
            if (keyframe != null) {
                float[] translation = keyframe.translation();
                float[] rotation = keyframe.leftRotation();
                if (translation != null && translation.length >= 3) localTranslation[i] = translation;
                if (rotation != null && rotation.length >= 4) localRotation[i] = rotation;
            }
            boolean keyedScale = keyframe != null && keyframe.scale() != null && keyframe.scale().length >= 3;
            // an effect left enlarged by the animation that just ended goes back to its resting size
            boolean restoreScale = restoreScales && scaleChanged[i] && !keyedScale;
            if (keyframe == null && !turned && !restoreScale) continue;
            final int index = i;
            final RavengardMobClip.Keyframe finalKeyframe = keyframe;
            part.editEntityMeta(ItemDisplayMeta.class, meta -> {
                Integer duration = finalKeyframe == null ? null : finalKeyframe.duration();
                if (duration != null && duration != partDuration[index]) {
                    partDuration[index] = duration;
                    meta.setTransformationInterpolationDuration(duration);
                }
                meta.setTransformationInterpolationStartDelta(0);
                float[] rotation = rotateRig(index, safeRotation(localRotation[index]), turn);
                Vec translation = rotateOffset(safeVec(localTranslation[index], Vec.ZERO), turn);
                if (partLift[index] != null) translation = translation.add(rotate(rotation, partLift[index]));
                meta.setTranslation(translation);
                meta.setLeftRotation(rotation);
                if (keyedScale) {
                    meta.setScale(vec(finalKeyframe.scale()));
                    scaleChanged[index] = true;
                } else if (restoreScale) {
                    meta.setScale(vec(clip.parts().get(index).scale()));
                    scaleChanged[index] = false;
                }
            });
        }
        restoreScales = false;
        if ((moved || oneShot) && !dying && isRigEntityActive(healthBar)) {
            healthBar.teleport(position.add(0, barHeight(), 0));
        }

        frame++;
    }

    private static float wrapDegrees(float degrees) {
        while (degrees > 180) degrees -= 360;
        while (degrees < -180) degrees += 360;
        return degrees;
    }

    /**
     * The melee goal keeps its own cached target and never writes the creature's, so the rig
     * would have nothing to face once pathfinding stops beside the player.
     */
    private void updateTarget() {
        if (dying || (clip.walkSpeed() <= 0 && !stationaryCaster)) return;
        Entity target = getTarget();
        if (target != null && (!isTargetable(target)
                || target.getPosition().distanceSquared(position) > CHASE_LOSE_RANGE * CHASE_LOSE_RANGE)) {
            setTarget(null);
            target = null;
        }
        if (target == null) {
            Player nearest = null;
            double best = CHASE_ACQUIRE_RANGE * CHASE_ACQUIRE_RANGE;
            for (Player player : instance.getPlayers()) {
                if (!isTargetable(player)) continue;
                double distance = player.getPosition().distanceSquared(position);
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            if (nearest != null) {
                setTarget(nearest);
            }
        }
    }

    private static volatile java.util.function.Predicate<Player> targetGate = player -> true;

    public static void setTargetGate(java.util.function.Predicate<Player> gate) {
        targetGate = gate;
    }

    static boolean isTargetable(Entity entity) {
        return entity instanceof Player player
                && targetGate.test(player)
                && !(player instanceof net.swofty.type.ravengardgeneric.user.RavengardPlayer ravengardPlayer
                && net.swofty.type.ravengardgeneric.classes.RavengardAbilityService.hasBuff(
                        ravengardPlayer, net.swofty.type.ravengardgeneric.classes.RavengardAbility.SHADOWS))
                && !player.isRemoved()
                && !player.isDead()
                && player.getGameMode() != net.minestom.server.entity.GameMode.SPECTATOR
                && player.getGameMode() != net.minestom.server.entity.GameMode.CREATIVE;
    }

    private static final double CHASE_ACQUIRE_RANGE = 14;
    private static final double CHASE_LOSE_RANGE = 20;

    private void tickStationaryCaster() {
        if (!stationaryCaster || dying || attackCooldown > 0) return;
        Entity target = getTarget();
        if (!(target instanceof LivingEntity living) || target.isRemoved()
                || position.distance(target.getPosition()) > CASTER_RANGE
                || !hasLineOfSight(target)) {
            return;
        }
        attackCooldown = CASTER_COOLDOWN_TICKS;
        playAttackSound();
        Pos impact = target.getPosition().add(0, Math.max(0.8, target.getBoundingBox().height() * 0.5), 0);
        instance.sendGroupedPacket(new ParticlePacket(Particle.FLAME,
                impact.x(), impact.y(), impact.z(), 0.28f, 0.35f, 0.28f, 0.02f, 16));
        living.damage(net.minestom.server.entity.damage.Damage.fromEntity(this, casterDamage()));
        if (!attackReported) {
            attackReported = true;
            org.tinylog.Logger.info(
                    "Ravengard caster attack: type={} entity={} target={} distance={} targetHealth={}",
                    clip.name(), getEntityId(), target.getEntityId(), position.distance(target.getPosition()),
                    living.getHealth());
        }
    }

    private float playerDamage() {
        float damage = PLAYER_DAMAGE_PER_HIT;
        if (appearanceType.endsWith("_elite")) {
            damage *= 1.25f;
        }
        return damage;
    }

    private float casterDamage() {
        float damage = CASTER_DAMAGE_PER_HIT;
        if (appearanceType.endsWith("_elite")) {
            damage *= 1.25f;
        }
        return damage;
    }

    private void playAttackSound() {
        if (instance == null) return;
        if (clip.name().contains("mimic")) {
            instance.playSound(net.kyori.adventure.sound.Sound.sound(
                            net.kyori.adventure.key.Key.key("hypixel_ravengard", "entity.mimic.munch"),
                            net.kyori.adventure.sound.Sound.Source.HOSTILE, 0.9f, 1.0f),
                    position.x(), position.y(), position.z());
            return;
        }
        RavengardSounds.play(instance, position, RavengardSounds.Cue.MOB_ATTACK);
    }

    private float updateRigYaw(Pos previous, boolean moved) {
        if (dying) return rigYaw;
        Float desired = null;
        Entity target = getTarget();
        if (target != null && !target.isRemoved()) {
            double dx = target.getPosition().x() - position.x();
            double dz = target.getPosition().z() - position.z();
            if (dx * dx + dz * dz > 1e-4) {
                desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        } else if (moved) {
            double dx = position.x() - previous.x();
            double dz = position.z() - previous.z();
            if (dx * dx + dz * dz > 1e-6) {
                desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        }
        if (desired != null) {
            float delta = desired - rigYaw;
            while (delta > 180) delta -= 360;
            while (delta < -180) delta += 360;
            rigYaw += Math.clamp(delta, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
            while (rigYaw > 180) rigYaw -= 360;
            while (rigYaw < -180) rigYaw += 360;
        }
        return rigYaw;
    }

    private static Vec vec(float[] values) {
        return new Vec(values[0], values[1], values[2]);
    }

    private static Vec safeVec(float[] values, Vec fallback) {
        return values != null && values.length >= 3 ? vec(values) : fallback;
    }

    private static float[] safeRotation(float[] values) {
        return values != null && values.length >= 4 ? values : new float[]{0, 0, 0, 1};
    }

    private static Vec rotateOffset(Vec offset, float yaw) {
        double theta = Math.toRadians(yaw);
        double sin = Math.sin(theta), cos = Math.cos(theta);
        return new Vec(offset.x() * cos + offset.z() * sin, offset.y(),
                -offset.x() * sin + offset.z() * cos);
    }

    private static Vec rotate(float[] rotation, Vec vector) {
        double x = rotation[0], y = rotation[1], z = rotation[2], w = rotation[3];
        double cx = y * vector.z() - z * vector.y();
        double cy = z * vector.x() - x * vector.z();
        double cz = x * vector.y() - y * vector.x();
        double dx = y * cz - z * cy;
        double dy = z * cx - x * cz;
        double dz = x * cy - y * cx;
        return new Vec(vector.x() + 2 * (w * cx + dx), vector.y() + 2 * (w * cy + dy),
                vector.z() + 2 * (w * cz + dz));
    }

    private boolean isRigEntityActive(Entity entity) {
        return !rigRemoved && entity != null && !entity.isRemoved() && entity.getInstance() == instance;
    }

    private float[] rotateRig(int part, float[] rotation, float yaw) {
        if (part < 0 || part >= lastSentRotation.length) return safeRotation(rotation);
        rotation = safeRotation(rotation);
        double theta = Math.toRadians(yaw) / 2.0;
        float sin = (float) Math.sin(theta), cos = (float) Math.cos(theta);
        float rx = rotation[0], ry = rotation[1], rz = rotation[2], rw = rotation[3];
        float[] composed = {cos * rx + sin * rz, cos * ry + sin * rw,
                cos * rz - sin * rx, cos * rw - sin * ry};

        float[] last = lastSentRotation[part];
        if (last != null) {
            float dot = last[0] * composed[0] + last[1] * composed[1]
                    + last[2] * composed[2] + last[3] * composed[3];
            if (dot < 0) {
                for (int i = 0; i < 4; i++) composed[i] = -composed[i];
            }
        }
        lastSentRotation[part] = composed;
        return composed;
    }

    public static void startTicking() {
        MinecraftServer.getSchedulerManager()
                .buildTask(() -> MOBS.forEach(mob -> {
                    try {
                        mob.tickRig();
                    } catch (Exception exception) {
                        if (REPORTED_TICK_FAILURES.add(mob.getUuid())) {
                            org.tinylog.Logger.error(exception,
                                    "Ravengard mob tick failed: type={} entity={} pos={}",
                                    mob.clip.name(), mob.getEntityId(), mob.getPosition());
                        }
                    }
                }))
                .repeat(net.minestom.server.timer.TaskSchedule.tick(1))
                .schedule();
    }
}
