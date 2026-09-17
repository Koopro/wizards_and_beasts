package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.event.skill.SkillEvents;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellProficiencySyncS2CPayload;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Centralized successful-hit bookkeeping for spell proficiency progression.
 */
public final class SpellProficiencyTracker {
    private SpellProficiencyTracker() {}

    public static void recordSuccessfulHit(ServerPlayer player, String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return;
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        int oldHits = data.getSuccessfulHits(spellId);
        data.incrementSuccessfulHits(spellId);
        int newHits = data.getSuccessfulHits(spellId);
        if (ModuleManager.isEnabled(Module.PROFICIENCY)) {
            float current = data.getSpellProficiency(spellId);
            float baseIncrement = 0.002f * studyRate(player);
            float effectiveIncrement = baseIncrement * (current >= 0.8f ? (1.0f - current) : 1.0f);
            float updated = Math.min(1.0f, current + Math.max(0.0f, effectiveIncrement));
            data.setSpellProficiency(spellId, updated);
            SpellProficiencySyncS2CPayload.sendTo(player, spellId, updated);
        }
        SkillEvents.checkProficiencyMilestone(player, spellId, oldHits, newHits);
        // Landing spells is what trains PRECISION. Hooked here rather than at the six call sites
        // that reach this method, so every path that counts as a hit counts as practice.
        at.koopro.wizardsandbeasts.stats.StatTraining.onSpellHit(player);
        ItemStack wandStack = at.koopro.wizardsandbeasts.util.WandHelper.getWandStack(player);
        if (!wandStack.isEmpty()) {
            at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService.onSuccessfulCast(
                    player, wandStack, at.koopro.wizardsandbeasts.spell.core.Spells.byId(spellId));
        }
        SpellDataDeltaS2CPayload.sendTo(
                player,
                spellId,
                data.getCooldownExpiry(spellId),
                data.getCastCount(spellId),
                newHits,
                data.getGlobalCooldownEndTick());
    }

    /**
     * How much faster this player's practice pays off, from KNOWLEDGE.
     *
     * <p>KNOWLEDGE's one gameplay consequence, and the one place it is read. With PLAYER_STATS off
     * there is no stat to read and everyone trains at the unmodified rate — never a penalty, because
     * a module being disabled must not make the game harder than a module being enabled at zero.
     */
    private static float studyRate(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) {
            return 1.0f;
        }
        return at.koopro.wizardsandbeasts.stats.StatEffects.studyRate(
                at.koopro.wizardsandbeasts.stats.PlayerStatsAPI.getStat(
                        player, at.koopro.wizardsandbeasts.stats.PlayerStat.KNOWLEDGE));
    }
}
