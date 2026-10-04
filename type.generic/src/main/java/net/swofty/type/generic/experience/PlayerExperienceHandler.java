package net.swofty.type.generic.experience;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.sound.SoundEvent;
import net.swofty.commons.text.Text;
import net.swofty.type.generic.data.HypixelDataHandler;
import net.swofty.type.generic.data.datapoints.DatapointHypixelExperience;
import net.swofty.type.generic.user.HypixelPlayer;

@RequiredArgsConstructor
public class PlayerExperienceHandler {
    private final HypixelPlayer player;

    public long getExperience() {
        return player.getDataHandler()
                .get(HypixelDataHandler.Data.HYPIXEL_EXPERIENCE, DatapointHypixelExperience.class)
                .getValue();
    }

    public void setExperience(long xp) {
        player.getDataHandler()
                .get(HypixelDataHandler.Data.HYPIXEL_EXPERIENCE, DatapointHypixelExperience.class)
                .setValue(xp);
    }

    public boolean addExperience(long amount) {
        long oldXP = getExperience();
        long newXP = oldXP + amount;

        int oldLevel = HypixelExperience.xpToLevel(oldXP);
        int newLevel = HypixelExperience.xpToLevel(newXP);

        setExperience(newXP);

        player.sendMessage("<b>+<3>{:,} Hypixel 经验", amount);

        if (newLevel > oldLevel) {
            for (int level = oldLevel + 1; level <= newLevel; level++) {
                onLevelUp(level);
            }
            return true;
        }

        return false;
    }

    public int getLevel() {
        return HypixelExperience.xpToLevel(getExperience());
    }

    public double getProgressToNextLevel() {
        return HypixelExperience.progressToNextLevel(getExperience());
    }

    public long getXPForNextLevel() {
        return HypixelExperience.xpForNextLevel(getExperience());
    }

    public long getXPInCurrentLevel() {
        return HypixelExperience.getXPInCurrentLevel(getExperience());
    }

    public double getCoinMultiplier() {
        return HypixelExperience.getCoinMultiplier(getLevel());
    }

    public boolean isVeteran() {
        return HypixelExperience.isVeteran(getLevel());
    }

    public Text getFormattedLevel() {
        return HypixelExperience.formatLevel(getLevel());
    }

    private void onLevelUp(int newLevel) {
        player.sendMessage("<6><l>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("<b><l>升级了！</l><7>你的 Hypixel 等级现在是 <e>{}", newLevel);
        player.sendMessage("");

        double multiplier = HypixelExperience.getCoinMultiplier(newLevel);
        int prevLevel = newLevel - 1;
        double prevMultiplier = HypixelExperience.getCoinMultiplier(prevLevel);
        if (multiplier > prevMultiplier) {
            player.sendMessage("<6>新特权！<e>硬币倍率：<6>{}x", multiplier);
        }

        if (newLevel == 100) {
            player.sendMessage("<d><l>老玩家身份已解锁！");
            player.sendMessage("<7>可领取老玩家专属奖励！");
        }

        player.sendMessage("<6><l>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1.0f, 1.0f));
    }
}
