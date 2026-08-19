package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a wand does to a spell, as tooltip lines.
 *
 * <p>These numbers were invisible before this existed. The tooltip listed a wand's identity and
 * condition — wood, core, integrity, corruption — and never what any of it was worth, so the only way
 * to learn that a rowan wand casts at 0.92× was to read the datapack.
 *
 * <p><b>Contribution, not outcome.</b> Every line is a delta from neutral for <em>this wand</em>. It
 * deliberately does not attempt an effective damage figure: a tooltip has no spell, no proficiency and
 * no caster stats in scope, and the cast pipeline applies six further modifiers plus a bounded
 * {@code ModifierStack} on top. A number claiming to be the real one would be wrong in a way nobody
 * could check, which is exactly the defect {@code /wandb magic spell info} already has.
 *
 * <p>The source is {@code WandStatsResolver.resolve} — the same call the cast path makes — so the
 * stated contribution and the applied contribution cannot drift apart.
 *
 * <p>Wood, core, length and flexibility are folded into one resolved set rather than itemised. The
 * player wants to know what the wand does, not which pillar did it.
 *
 * <p>Rows appear only when they say something: a contribution that rounds to zero is omitted rather
 * than printed as {@code +0%}. Missing or unresolvable definitions therefore degrade to silence,
 * matching {@link WandLoreNames}' rule that a tooltip never renders an error.
 */
@NullMarked
public final class WandCastLines {

    private WandCastLines() {}

    /**
     * Appends one line per non-neutral contribution.
     *
     * <p>Percentages use an ASCII hyphen rather than a typographic minus on purpose: the minus sign is
     * not in Minecraft's bitmap font providers and would fall through to unifont, which is a separately
     * downloaded asset. A tooltip that renders a missing-glyph box for the sign of a number is worse
     * than one that uses a plain hyphen.
     */
    public static void append(WandStats stats, Consumer<Component> lines) {
        for (Component line : build(stats)) {
            lines.accept(line);
        }
    }

    /** Visible for testing: the lines this would append, in order. */
    public static List<Component> build(WandStats stats) {
        List<Component> out = new ArrayList<>();

        // Multipliers, as a percentage away from 1.0. Higher damage and range are good; a higher
        // cooldown means waiting longer, so its sign is read the other way round.
        addMultiplier(out, stats.damageMultiplier(), "wandcraft.tooltip.cast.damage", true);
        addMultiplier(out, stats.cooldownMultiplier(), "wandcraft.tooltip.cast.cooldown", false);
        addMultiplier(out, stats.rangeMultiplier(), "wandcraft.tooltip.cast.range", true);

        // Fizzle is already a delta, and it is a chance of failing, so more of it is worse.
        addPoints(out, stats.fizzleChance(), "wandcraft.tooltip.cast.misfire", false);

        // Iterated over the enum rather than the map so the order is the enum's, not a hash order —
        // two wands with the same bonuses must list them the same way round.
        Map<SpellCategory, Float> bonuses = stats.categoryDamageBonus();
        for (SpellCategory category : SpellCategory.values()) {
            Float bonus = bonuses.get(category);
            if (bonus == null) {
                continue;
            }
            int percent = Math.round(bonus * 100.0f);
            if (percent == 0) {
                continue;
            }
            out.add(Component.translatable("wandcraft.tooltip.cast.category",
                            categoryName(category), signed(percent))
                    .withStyle(tone(percent > 0)));
        }
        return out;
    }

    private static void addMultiplier(List<Component> out, float multiplier, String key,
                                      boolean higherIsBetter) {
        int percent = Math.round((multiplier - 1.0f) * 100.0f);
        if (percent == 0) {
            return;
        }
        out.add(Component.translatable(key, signed(percent))
                .withStyle(tone(higherIsBetter == (percent > 0))));
    }

    private static void addPoints(List<Component> out, float delta, String key,
                                  boolean higherIsBetter) {
        int percent = Math.round(delta * 100.0f);
        if (percent == 0) {
            return;
        }
        out.add(Component.translatable(key, signed(percent))
                .withStyle(tone(higherIsBetter == (percent > 0))));
    }

    /** {@code +12%} / {@code -8%}. */
    private static String signed(int percent) {
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    /**
     * Matches the existing tooltip's own vocabulary — integrity is green, corruption is dark red — so
     * a cast row reads as good or bad by the same rule as the lines above it.
     */
    private static ChatFormatting tone(boolean beneficial) {
        return beneficial ? ChatFormatting.GREEN : ChatFormatting.DARK_RED;
    }

    private static Component categoryName(SpellCategory category) {
        return Component.translatable("spell_category.wizards_and_beasts." + category.getSerializedName());
    }
}
