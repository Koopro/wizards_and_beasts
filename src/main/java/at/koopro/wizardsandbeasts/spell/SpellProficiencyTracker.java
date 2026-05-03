package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.event.SkillEvents;
import at.koopro.wizardsandbeasts.network.SpellDataDeltaS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.wand.cast.WandAllegianceSystem;
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
        SkillEvents.checkProficiencyMilestone(player, spellId, oldHits, newHits);
        ItemStack wandStack = at.koopro.wizardsandbeasts.util.WandHelper.getWandStack(player);
        if (!wandStack.isEmpty() && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            WandAllegianceSystem.onSuccessfulCast(player, wandStack, level);
        }
        SpellDataDeltaS2CPacket.sendTo(
                player,
                spellId,
                data.getCooldownExpiry(spellId),
                data.getCastCount(spellId),
                newHits);
    }
}
