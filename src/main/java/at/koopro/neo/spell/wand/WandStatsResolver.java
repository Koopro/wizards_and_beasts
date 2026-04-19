package at.koopro.neo.spell.wand;

import at.koopro.neo.item.WandItem;
import at.koopro.neo.item.wand.WandCore;
import at.koopro.neo.item.wand.WandFlexibility;
import at.koopro.neo.item.wand.WandLength;
import at.koopro.neo.item.wand.WandWood;
import at.koopro.neo.registry.ModDataComponents;
import at.koopro.neo.spell.SpellCategory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Resolves a wand's four soul attributes (wood, core, length, flexibility) into
 * a single {@link WandStats}. Lore-faithful starter table — every modifier is
 * tunable in one place and intentionally moderate so that no single component
 * dominates the math (the pillar that does the heavy lifting is the core).
 *
 * <p>To make these JSON-driven later (Stage 4 or beyond), replace the per-enum
 * {@code apply*} methods with a {@code Map<E, WandStatsContribution>} loaded
 * from a datapack.
 */
public final class WandStatsResolver {

    private WandStatsResolver() {}

    /**
     * Returns the combined {@link WandStats} for a wand stack. If the stack is
     * not a wand or has missing attributes, the missing pieces fall back to
     * neutral contributions so the result is always usable in math.
     */
    public static WandStats resolve(@Nullable ItemStack wandStack) {
        if (wandStack == null || wandStack.isEmpty() || !(wandStack.getItem() instanceof WandItem)) {
            return WandStats.NEUTRAL;
        }

        WandStats.Builder b = WandStats.builder();
        applyCore(b, wandStack.get(ModDataComponents.WAND_CORE.get()));
        applyWood(b, wandStack.get(ModDataComponents.WAND_WOOD.get()));
        applyLength(b, wandStack.get(ModDataComponents.WAND_LENGTH.get()));
        applyFlexibility(b, wandStack.get(ModDataComponents.WAND_FLEXIBILITY.get()));
        return b.build();
    }

    // ── Cores: the dominant flavor knob ──────────────────────────────────

    private static void applyCore(WandStats.Builder b, @Nullable WandCore core) {
        if (core == null) return;
        switch (core) {
            case PHOENIX_FEATHER -> b
                    .addCategoryDamageBonus(SpellCategory.COMBAT, 0.15f)
                    .mulCooldown(0.95f);
            case DRAGON_HEARTSTRING -> b
                    .mulDamage(1.15f)
                    .addFizzle(0.05f);
            case UNICORN_HAIR -> b
                    .mulRange(1.10f)
                    .addCategoryDamageBonus(SpellCategory.DEFENSE, 0.10f)
                    .addFizzle(-0.03f);
            case THESTRAL_TAIL -> b
                    .addCategoryDamageBonus(SpellCategory.DARK_ARTS, 0.20f)
                    .addFizzle(0.02f);
        }
    }

    // ── Woods: smaller modifiers, often category-flavored ────────────────

    private static void applyWood(WandStats.Builder b, @Nullable WandWood wood) {
        if (wood == null) return;
        switch (wood) {
            case ELDER -> b
                    .mulDamage(1.05f)
                    .mulCooldown(0.95f);
            case YEW -> b.addCategoryDamageBonus(SpellCategory.DARK_ARTS, 0.05f);
            case HOLLY -> b.addCategoryDamageBonus(SpellCategory.COMBAT, 0.05f);
            case ROWAN -> b
                    .addCategoryDamageBonus(SpellCategory.DEFENSE, 0.05f)
                    .addFizzle(-0.02f);
        }
    }

    // ── Length: range / damage / cooldown trade ──────────────────────────

    private static void applyLength(WandStats.Builder b, @Nullable WandLength length) {
        if (length == null) return;
        switch (length) {
            case SHORT -> b.mulRange(0.90f).mulDamage(0.95f).mulCooldown(0.90f);
            case MEDIUM -> b.mulRange(0.97f);
            case STANDARD -> {
                // neutral
            }
            case LONG -> b.mulRange(1.10f).mulDamage(1.05f).mulCooldown(1.05f);
        }
    }

    // ── Flexibility: precision (fizzle / cooldown) trade ─────────────────

    private static void applyFlexibility(WandStats.Builder b, @Nullable WandFlexibility flex) {
        if (flex == null) return;
        switch (flex) {
            case RIGID -> b.addFizzle(-0.02f).mulDamage(1.03f);
            case SLIGHTLY_YIELDING -> b.addFizzle(-0.01f);
            case SUPPLE -> b.mulCooldown(0.95f);
            case QUITE_FLEXIBLE -> b.addFizzle(0.02f).mulCooldown(0.92f);
        }
    }
}
