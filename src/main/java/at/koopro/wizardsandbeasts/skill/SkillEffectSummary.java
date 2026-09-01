package at.koopro.wizardsandbeasts.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a node's {@link SkillEffect} list as lines a player can read.
 *
 * <h2>Why derive it rather than write it</h2>
 * <p>A skill node carries two independent things: a {@code description} string an author typed, and
 * an {@code effects} list the game actually executes. The tooltip only ever showed the first. Prose
 * and data drift — a rebalance edits the number, nobody edits the sentence — and the player has no
 * way to tell which one the game will honour. Deriving the summary from {@code effects} means the
 * tooltip cannot lie: if the line says +15%, that number came out of the same field the cast reads.
 *
 * <p>The description stays, as flavour. It is the effect lines that are load-bearing.
 *
 * <h2>Honesty about what is not implemented</h2>
 * <p>{@link #describe} answers {@code null} for an effect type nothing consumes, and the caller
 * drops the line rather than printing a promise. That is not hypothetical: {@code learn_spell},
 * {@code grant_ability} and {@code ability_refinement} are declarable and ship on zero nodes, and
 * {@code spell_damage_bonus}/{@code spell_cooldown_reduction} were declared on seventeen nodes and
 * consumed by nothing at all until the cast path was fixed to read them.
 *
 * <p>Client-safe: no player, no server types, so the same code renders the tooltip and can be
 * checked in a unit test.
 */
@NullMarked
public final class SkillEffectSummary {

    private static final String KEY_PREFIX = "skill.wizards_and_beasts.effect.";

    private SkillEffectSummary() {}

    /**
     * One line per effect the node has, at {@code level}.
     *
     * <p>{@code level} is the level being described, not the level the player holds: pass
     * {@code max(1, currentLevel)} to answer "what does this node give me", and {@code 1} to answer
     * "what does one point buy". Effects nothing consumes are omitted.
     */
    public static List<Component> lines(Skill skill, int level) {
        int effective = Math.max(1, level);
        List<Component> out = new ArrayList<>(skill.getEffects().size());
        for (SkillEffect effect : skill.getEffects()) {
            Component line = describe(effect, effective);
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * A single effect at a given level, or {@code null} when nothing in the game consumes this
     * effect type and printing it would be a promise the game does not keep.
     */
    public static Component describe(SkillEffect effect, int level) {
        return switch (effect) {
            case SkillEffect.SpellDamageBonus e ->
                    line("spell_damage", percent(e.bonusPerLevel() * level), spellName(e.spellId()));
            case SkillEffect.SpellCooldownReduction e ->
                    line("spell_cooldown", percent(-e.reductionPerLevel() * level), spellName(e.spellId()));
            case SkillEffect.CategoryDamageBonus e ->
                    line("category_damage", percent(e.bonusPerLevel() * level), categoryName(e.category().name()));
            case SkillEffect.CategoryCooldownReduction e ->
                    line("category_cooldown", percent(-e.reductionPerLevel() * level), categoryName(e.category().name()));
            case SkillEffect.PassiveAttribute e -> attributeLine(e, level);
            case SkillEffect.GameplayBonus e ->
                    line("gameplay_bonus", percent(e.perLevel() * level), statName(e.stat()));
            case SkillEffect.UnlockAbility e ->
                    line("unlock_ability", abilityName(e.abilityId()));
            case SkillEffect.LearnSpell e -> line("learn_spell", spellName(e.spellId()));
            // Consumed by the grant layer, but with no line to describe themselves yet. Returning
            // null keeps the tooltip silent rather than advertising a benefit it cannot name; see
            // isImplemented, which keeps both out of shippable datapacks for exactly this reason.
            case SkillEffect.GrantAbility ignored -> null;
            case SkillEffect.AbilityRefinement ignored -> null;
        };
    }

    /**
     * Whether this effect type reaches a real system.
     *
     * <p>Exposed so a datapack test can fail on a node that ships an inert effect, instead of the
     * omission only ever showing up as a blank space in a tooltip.
     */
    public static boolean isImplemented(SkillEffect.Type type) {
        return switch (type) {
            case SPELL_DAMAGE_BONUS, SPELL_COOLDOWN_REDUCTION,
                 CATEGORY_DAMAGE_BONUS, CATEGORY_COOLDOWN_REDUCTION,
                 PASSIVE_ATTRIBUTE, UNLOCK_ABILITY, GAMEPLAY_BONUS,
                 // Wired 2026-08-22: SkillSystemAPI.applyImmediateEffects teaches the spell at
                 // allocation and revokeWebTaughtSpells takes it back on refund. Before that this
                 // was the one shipped-but-inert type, and it read `false` correctly.
                 LEARN_SPELL -> true;
            // These two DO reach a system — SkillNodeAbilityGrantSource reads GrantAbility and
            // AbilityModifiers reads AbilityRefinement — but neither has a summary line yet, so a
            // node shipping one would allocate with a blank tooltip. The flag gates "safe to ship
            // in a datapack", not "the effect exists", and stays false until they can describe
            // themselves. No node uses either today.
            case GRANT_ABILITY, ABILITY_REFINEMENT -> false;
        };
    }

    // -- pieces ----------------------------------------------------------------------------------

    /**
     * {@code passive_attribute} is a flat amount, not a percentage, and only three attribute ids are
     * wired ({@code Skill#deriveNodeEffects}). An unwired id gets no line rather than a line
     * claiming a stat the applicator will silently skip.
     */
    private static Component attributeLine(SkillEffect.PassiveAttribute effect, int level) {
        String key = switch (effect.attributeId()) {
            case "max_health" -> "attribute.max_health";
            case "movement_speed" -> "attribute.movement_speed";
            case "armor" -> "attribute.armor";
            default -> null;
        };
        if (key == null) {
            return null;
        }
        return line(key, signed(effect.amountPerLevel() * level));
    }

    private static MutableComponent line(String suffix, Object... args) {
        return Component.translatable(KEY_PREFIX + suffix, args);
    }

    /**
     * {@code 0.15f} renders as {@code +15%}.
     *
     * <p>Cooldown reductions are authored as positive numbers ({@code reductionPerLevel: 0.1} means
     * ten percent <em>faster</em>) and are negated by the caller before they get here, so the player
     * reads {@code -10% cooldown} rather than a {@code +10%} that means the opposite of what it says.
     */
    private static String percent(float fraction) {
        int value = Math.round(fraction * 100.0f);
        return (value >= 0 ? "+" : "") + value + "%";
    }

    private static String signed(double amount) {
        String text = amount == Math.rint(amount)
                ? String.valueOf((long) amount)
                : String.format(Locale.ROOT, "%.1f", amount);
        return amount >= 0 ? "+" + text : text;
    }

    /**
     * Spell ids are bare paths in node JSON ({@code "wingardium_leviosa"}).
     *
     * <p>The lang file carries two conventions for a spell's name — {@code spell.<mod>.<id>} and
     * {@code spell.<mod>.<id>.name}. The {@code .name} form is the one every spell referenced by a
     * skill node uses, so that is the one keyed here rather than a runtime probe of both.
     */
    private static Component spellName(String spellId) {
        return Component.translatable("spell.wizards_and_beasts." + spellId + ".name");
    }

    private static Component categoryName(String category) {
        return Component.translatable("spell.wizards_and_beasts.category."
                + category.toLowerCase(Locale.ROOT));
    }

    private static Component statName(GameplayStat stat) {
        return Component.translatable("skill.wizards_and_beasts.stat."
                + stat.name().toLowerCase(Locale.ROOT));
    }

    private static Component abilityName(String abilityId) {
        return Component.translatable("skill.wizards_and_beasts.ability." + abilityId);
    }
}
