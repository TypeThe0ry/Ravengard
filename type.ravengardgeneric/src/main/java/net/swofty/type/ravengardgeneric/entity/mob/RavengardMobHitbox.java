package net.swofty.type.ravengardgeneric.entity.mob;

import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.other.InteractionMeta;

final class RavengardMobHitbox extends LivingEntity {
    private final RavengardMob owner;

    RavengardMobHitbox(RavengardMob owner, double width, double height) {
        super(EntityType.INTERACTION);
        this.owner = owner;
        setNoGravity(true);
        setAutoViewable(true);
        setBoundingBox(width, height, width);
        editEntityMeta(InteractionMeta.class, meta -> {
            meta.setWidth((float) width);
            meta.setHeight((float) height);
            meta.setHasNoGravity(true);
            meta.setResponse(true);
        });
    }

    RavengardMob owner() {
        return owner;
    }
}
