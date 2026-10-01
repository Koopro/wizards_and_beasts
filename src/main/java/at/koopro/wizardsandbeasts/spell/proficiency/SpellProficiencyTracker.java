package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.event.skill.SkillEvents;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellProficiencySyncS2CPayload;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Centralized successful-hit bookkeeping for spell proficiency progression.
 *
 * <p>Every successful use still reaches the wand ({@code WandAllegianceService.onSuccessfulCast}) — a wand learns its
 * owner through use. Only {@link SpellPractice#DAILY_PRACTICE} uses of one spell a day count as practice: its hit
 * count (the tier), its place on the power curve, and the PRECISION they train. See {@link SpellPractice}.
 */
public final class SpellProficiencyTracker {
    private SpellProficiencyTracker() {}

    public static void recordSuccessfulHit(ServerPlayer player, String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return;
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        long today = SpellPractice.day(player.level().getGameTime());
        boolean practice = SpellPractice.counts(data.getPracticeToday(spellId, today));
        int oldHits = data.getSuccessfulHits(spellId);
        if (practice) {
            int practisedToday = data.recordPractice(spellId, today);
            data.incrementSuccessfulHits(spellId);
            if (practisedToday == SpellPractice.DAILY_PRACTICE) {
                // Told once, the moment it happens: otherwise a stalled tier reads as a bug.
                PlayerFeedback.actionBar(player, Component.translatable(
                        "spell.wizards_and_beasts.practice.rested", spellName(spellId)));
            }
        }
        int newHits = data.getSuccessfulHits(spellId);
        if (practice && ModuleManager.isEnabled(Module.PROFICIENCY)) {
            float updated = SpellPractice.afterPractice(data.getSpellProficiency(spellId), newHits, studyRate(player));
            data.setSpellProficiency(spellId, updated);
            SpellProficiencySyncS2CPayload.sendTo(player, spellId, updated);
        }
        if (practice) {
            SkillEvents.checkProficiencyMilestone(player, spellId, oldHits, newHits);
            // Landing spells is what trains PRECISION. Hooked here rather than at the six call sites
            // that reach this method, so every path that counts as a hit counts as practice.
            at.koopro.wizardsandbeasts.stats.StatTraining.onSpellHit(player);
        }
        ItemStack wandStack = at.koopro.wizardsandbeasts.util.WandHelper.getWandStack(player);
        if (!wandStack.isEmpty()) {
            at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService.onSuccessfulCast(
                    player, wandStack, at.koopro.wizardsandbeasts.spell.core.Spells.byId(spellId), practice);
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
    /** A spell's name for a message, falling back to its id if the spell is gone. */
    private static Component spellName(String spellId) {
        var spell = Spells.byId(spellId);
        return spell == null ? Component.literal(spellId) : Component.translatable(spell.getDisplayName());
    }

    private static float studyRate(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) {
            return 1.0f;
        }
        return at.koopro.wizardsandbeasts.stats.StatEffects.studyRate(
                at.koopro.wizardsandbeasts.stats.PlayerStatsAPI.getStat(
                        player, at.koopro.wizardsandbeasts.stats.PlayerStat.KNOWLEDGE));
    }
}
