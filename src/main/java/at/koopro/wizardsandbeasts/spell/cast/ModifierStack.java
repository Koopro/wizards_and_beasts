package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cast-scoped multiplier accumulator, and the one owner of how a cast's channels combine.
 *
 * <h2>Three channels, not one bag</h2>
 * <p>{@link #multiplyDamage} feeds the <em>situational</em> channel — wand corruption and
 * allegiance, dark corruption, vocation, Niffler happiness, player stats: everything specific to
 * this cast in this moment. Proficiency and the skill web each get their own slot, set once by
 * their owning system, because the tooltip has to be able to say <em>which</em> of the three a
 * number came from, and because a channel that can only be set once cannot be applied twice.
 *
 * <p>It could be applied twice before. {@code SkillSystemAPI} pushed the {@code Proficiency} enum
 * tier into the same bag that {@code ProficiencyScaler}'s float curve was later multiplied into by
 * the caller — two systems, one counter, two multiplications.
 *
 * <p>{@link #finalDamage()} and {@link #finalCooldown()} compose the three through
 * {@link SpellPower}, so no call site multiplies channels together itself and every call site is
 * bounded the same way.
 */
public final class ModifierStack {

    /**
     * Legacy bounds, kept only so existing references still read. The live bounds are
     * {@link SpellPower.Bounds} and come from config; these two constants no longer decide anything.
     *
     * @deprecated read {@link SpellPower#configuredBounds()} instead.
     */
    @Deprecated(forRemoval = false)
    public static final float HARD_CAP = 3.0f;
    /** @deprecated see {@link #HARD_CAP}. */
    @Deprecated(forRemoval = false)
    public static final float HARD_FLOOR = 0.25f;

    /** Situational channel: multiplied into freely by any system with a reason. */
    private float damage = 1.0f;
    private float cooldown = 1.0f;
    private float misfireChance = 0.0f;

    /** Proficiency channel: how well this caster knows this spell. Set once, by one system. */
    private float proficiencyDamage = 1.0f;
    private float proficiencyCooldown = 1.0f;

    /** Skill-web channel: category multiplier times the node's per-spell bonus. Set once. */
    private float skillDamage = 1.0f;
    private float skillCooldown = 1.0f;

    private final List<String> provenance = new ArrayList<>();

    public void multiplyDamage(float factor, String source) {
        damage *= factor;
        provenance.add(source + ":damage*x" + factor);
    }

    public void multiplyCooldown(float factor, String source) {
        cooldown *= factor;
        provenance.add(source + ":cooldown*x" + factor);
    }

    public void addMisfireChance(float chance, String source) {
        misfireChance += chance;
        provenance.add(source + ":misfire+" + chance);
    }

    // -- named channels --------------------------------------------------------------------------

    /**
     * Set the proficiency channel. Assignment rather than multiplication: proficiency has exactly
     * one source ({@code ProficiencyScaler}, gated on {@code Module.PROFICIENCY}), and a second
     * caller overwriting the first is a bug that shows up immediately, where a second caller
     * multiplying the first is a bug that hides in the damage numbers.
     */
    public void setProficiency(float damageMult, float cooldownMult) {
        this.proficiencyDamage = damageMult;
        this.proficiencyCooldown = cooldownMult;
        provenance.add("proficiency:damage=x" + damageMult + ",cooldown=x" + cooldownMult);
    }

    /** Set the skill-web channel. Same single-assignment reasoning as {@link #setProficiency}. */
    public void setSkill(float damageMult, float cooldownMult) {
        this.skillDamage = damageMult;
        this.skillCooldown = cooldownMult;
        provenance.add("skill_tree:damage=x" + damageMult + ",cooldown=x" + cooldownMult);
    }

    // -- composed results ------------------------------------------------------------------------

    /** The composed, soft-capped damage multiplier for this cast. */
    public float finalDamage() {
        return damageBreakdown().total();
    }

    /** The composed, clamped cooldown multiplier for this cast. Lower is faster. */
    public float finalCooldown() {
        return cooldownBreakdown().total();
    }

    /** The same number {@link #finalDamage()} returns, with its three channels still separable. */
    public SpellPower.Breakdown damageBreakdown() {
        return SpellPower.damage(damage, proficiencyDamage, skillDamage);
    }

    public SpellPower.Breakdown cooldownBreakdown() {
        return SpellPower.cooldown(cooldown, proficiencyCooldown, skillCooldown);
    }

    public float finalMisfireChance() {
        return Math.max(0.0f, Math.min(1.0f, misfireChance));
    }

    public List<String> provenance() {
        return Collections.unmodifiableList(provenance);
    }
}
