package net.swofty.type.ravengardgeneric.entity;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityProjectile;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.projectile.ArrowMeta;
import net.minestom.server.coordinate.Pos;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.event.actions.player.ActionPlayerBow;

public final class RavengardArrow extends EntityProjectile {
    private static final long MAX_LIFETIME_TICKS = 200;

    private final float damage;
    private boolean resolved;
    private java.util.function.Consumer<RavengardMob> onHit = mob -> {
    };

    public RavengardArrow(Entity shooter, float damage, boolean critical) {
        super(shooter, EntityType.ARROW);
        setHasPhysics(true);
        this.damage = damage;
        editEntityMeta(ArrowMeta.class, meta -> meta.setCritical(critical));
    }

    public float damage() {
        return damage;
    }

    /** Something an ability adds to this arrow's hit, such as a poison or a slow. */
    public RavengardArrow onHit(java.util.function.Consumer<RavengardMob> effect) {
        this.onHit = this.onHit.andThen(effect);
        return this;
    }

    public void applyHitEffects(RavengardMob mob) {
        onHit.accept(mob);
    }

    public boolean resolved() {
        return resolved;
    }

    public void markResolved() {
        resolved = true;
    }

    @Override
    public void tick(long time) {
        Pos previous = getPosition();
        super.tick(time);
        if (!resolved && getInstance() != null) {
            Pos current = getPosition();
            for (RavengardMob mob : RavengardMob.mobs()) {
                if (mob.getInstance() != getInstance() || mob.getHealth() <= 0 || mob.isDying()) continue;
                Pos target = mob.getPosition().add(0, 1.1, 0);
                if (segmentDistanceSquared(previous, current, target) <= 2.25 * 2.25
                        || current.distanceSquared(target) <= 2.25 * 2.25) {
                    ActionPlayerBow.tryHit(this, mob);
                    break;
                }
            }
        }
        if (!isRemoved() && getAliveTicks() >= MAX_LIFETIME_TICKS) {
            remove();
        }
    }

    private static double segmentDistanceSquared(Pos start, Pos end, Pos point) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double dz = end.z() - start.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared <= 1.0e-9) return start.distanceSquared(point);
        double t = ((point.x() - start.x()) * dx
                + (point.y() - start.y()) * dy
                + (point.z() - start.z()) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double x = start.x() + t * dx;
        double y = start.y() + t * dy;
        double z = start.z() + t * dz;
        double ox = x - point.x();
        double oy = y - point.y();
        double oz = z - point.z();
        return ox * ox + oy * oy + oz * oz;
    }
}
