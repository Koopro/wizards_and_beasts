package at.koopro.wizardsandbeasts.util;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Helper for checking whether a player has Magizoology skill unlocked,
 * used to apply creature-bonding bonuses in the Niffler system.
 */
public final class MagizoologyHelper {

    private MagizoologyHelper() {}

    /** True if the player has unlocked the entry-level Magizoology skill. */
    public static boolean isMagizoologist(Player player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        return data.getSkillLevel("creature_knowledge") > 0;
    }
}
