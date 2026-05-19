package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ProtegoProjectileHandler {

    private ProtegoProjectileHandler() {}

    public static void tryDeflect(ServerLevel level, SpellProjectileEntity projectile) {
        // Legacy hook retained for compatibility. Deflection is now handled by ProtegoShieldEntity.
    }

    public static int resolveTier(ServerPlayer caster, int heldTicks) {
        float prof = Spells.PROTEGO.getProficiencyScalar(caster);
        int darkDepth = SkillSystemAPI.countUnlockedSkillsInTree(caster, SkillTreeId.DARK_ARTS);
        int desired;
        if (heldTicks < 5) {
            desired = 0;
        } else if (heldTicks <= 15) {
            desired = 1;
        } else if (heldTicks <= 30) {
            desired = 2;
        } else {
            desired = darkDepth >= 3 ? 3 : 2;
        }
        while (desired > 0 && !meetsProficiency(desired, prof)) {
            desired--;
        }
        return desired;
    }

    private static boolean meetsProficiency(int tier, float prof) {
        return switch (tier) {
            case 0 -> true;
            case 1 -> prof >= 0.3f;
            case 2 -> prof >= 0.6f;
            case 3 -> prof >= 0.8f;
            default -> true;
        };
    }
}
