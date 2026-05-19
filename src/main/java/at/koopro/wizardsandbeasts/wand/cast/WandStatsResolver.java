package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.resources.Identifier;
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
        applyCore(b, resolveCore(wandStack));
        applyWood(b, resolveWood(wandStack));
        applyLength(b, resolveLength(wandStack));
        applyFlexibility(b, resolveFlexibility(wandStack));
        return b.build();
    }

    public static void applyToStack(ModifierStack stack, WandStats wandStats, Spell spell) {
        stack.multiplyDamage(wandStats.damageFor(spell), "wand");
        stack.multiplyCooldown(wandStats.cooldownFor(spell), "wand");
        if (wandStats.fizzleChance() > 0.0f) {
            stack.addMisfireChance(wandStats.fizzleChance(), "wand");
        }
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
            case VEELA_HAIR -> b
                    .mulCooldown(0.92f)
                    .addFizzle(0.03f);
            case TROLL_WHISKER -> b
                    .mulDamage(1.10f)
                    .mulCooldown(1.08f)
                    .addFizzle(0.04f);
            case WAMPUS_CAT_HAIR -> b
                    .addCategoryDamageBonus(SpellCategory.COMBAT, 0.12f)
                    .mulCooldown(0.97f);
            case THUNDERBIRD_TAIL_FEATHER -> b
                    .mulRange(1.15f)
                    .addCategoryDamageBonus(SpellCategory.DEFENSE, 0.08f)
                    .addFizzle(0.02f);
            case ROUGAROU_HAIR -> b
                    .addCategoryDamageBonus(SpellCategory.DARK_ARTS, 0.15f)
                    .mulDamage(1.05f)
                    .addFizzle(0.05f);
            case WHITE_RIVER_MONSTER_SPINE -> b
                    .mulDamage(1.08f)
                    .mulRange(1.08f)
                    .mulCooldown(1.05f);
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
            case UNYIELDING -> b.addFizzle(-0.03f).mulDamage(1.04f);
            case RIGID -> b.addFizzle(-0.02f).mulDamage(1.03f);
            case SOLID -> b.addFizzle(-0.01f);
            case SUPPLE -> b.mulCooldown(0.95f);
            case SPRINGY -> b.addFizzle(0.02f).mulCooldown(0.92f);
        }
    }

    private static WandCore resolveCore(ItemStack wandStack) {
        WandCore legacy = wandStack.get(ModDataComponents.WAND_CORE.get());
        if (legacy != null) {
            return legacy;
        }
        Identifier id = WandComponents.getCore(wandStack);
        return id == null ? null : WandCore.byName(id.getPath());
    }

    private static WandWood resolveWood(ItemStack wandStack) {
        WandWood legacy = wandStack.get(ModDataComponents.WAND_WOOD.get());
        if (legacy != null) {
            return legacy;
        }
        Identifier id = WandComponents.getWood(wandStack);
        return id == null ? null : WandWood.byName(id.getPath());
    }

    private static WandLength resolveLength(ItemStack wandStack) {
        WandLength legacy = wandStack.get(ModDataComponents.WAND_LENGTH.get());
        if (legacy != null) {
            return legacy;
        }
        Float inches = WandComponents.getLength(wandStack);
        if (inches == null) {
            return null;
        }
        if (inches <= 10.0f) return WandLength.SHORT;
        if (inches <= 12.0f) return WandLength.MEDIUM;
        if (inches >= 14.0f) return WandLength.LONG;
        return WandLength.STANDARD;
    }

    private static WandFlexibility resolveFlexibility(ItemStack wandStack) {
        WandFlexibility legacy = wandStack.get(ModDataComponents.WAND_FLEXIBILITY.get());
        if (legacy != null) {
            return legacy;
        }
        return WandComponents.getFlexibility(wandStack);
    }
}
