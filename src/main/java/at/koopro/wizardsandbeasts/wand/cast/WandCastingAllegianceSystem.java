package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * The wand's relationship with its caster, as it bears on one cast.
 *
 * <p>What the relationship is worth is decided in {@code WandAllegianceRules} and applied by
 * {@link WandAllegianceService}. This used to add a misfire chance for a poorly matched wand on top of its damage
 * change — a wand that served you badly did so at random — and read the {@code wand_allegiance_legacy} record,
 * which no longer agreed with the master the cast gate checks. It now applies a fixed power and cooldown for the
 * state the wand is in with this caster, the same every time, and the tooltip names that state.
 */
public final class WandCastingAllegianceSystem {

    private WandCastingAllegianceSystem() {}

    /**
     * Applies the relationship to the cast's modifier stack.
     *
     * <p>It used to also return a wood/heritage "compatibility" score that the cast context stored and nothing
     * ever read; bonding is decided by resonance and the bond itself, so the score is gone (2026-10-22).
     */
    public static void applyLayer(CastContext ctx, ServerLevel level) {
        ItemStack wand = ctx.wandStack();
        // No wand, no relationship: an empty-handed admin cast is not "another wizard's wand".
        if (!Config.enableWandAllegiance || !(wand.getItem() instanceof at.koopro.wizardsandbeasts.item.wand.WandItem)) {
            return;
        }
        WandBondState state = WandAllegianceService.castingState(ctx.caster(), wand);
        String source = "wand_bond_" + state.name().toLowerCase(Locale.ROOT);
        ctx.modifiers().multiplyDamage(
                WandAllegianceService.powerMultiplier(ctx.caster(), wand, level.registryAccess()), source);
        ctx.modifiers().multiplyCooldown(
                WandAllegianceService.cooldownMultiplier(ctx.caster(), wand, level.registryAccess()), source);

    }
}
