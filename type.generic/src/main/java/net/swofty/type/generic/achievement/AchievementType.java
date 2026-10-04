package net.swofty.type.generic.achievement;

import lombok.Getter;
import net.minestom.server.item.Material;

@Getter
public enum AchievementType {
    CHALLENGE("挑战", "一次性成就", Material.DIAMOND),
    TIERED("分级", "多级递进成就", Material.DIAMOND_BLOCK),
    SEASONAL("季节", "限时活动成就", Material.MAGMA_CREAM);

    private final String displayName;
    private final String description;
    private final Material material;

    AchievementType(String displayName, String description, Material material) {
        this.displayName = displayName;
        this.description = description;
        this.material = material;
    }
}
