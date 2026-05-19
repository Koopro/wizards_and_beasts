package at.koopro.wizardsandbeasts.brew;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;
import java.util.Objects;

/**
 * In-memory description of a brewable potion. Pure data — no Java logic of
 * its own. Created from a {@link at.koopro.wizardsandbeasts.brew.def.BrewDefinition} during
 * datapack reload (or registered programmatically by addon mods).
 *
 * <p>Mirrors the {@link at.koopro.wizardsandbeasts.spell.Spell} hierarchy intentionally:
 * brewing pillar is data-driven from day one because there are dozens of
 * potions in the wizarding canon and each one is just "drink → apply effects
 * with these durations and amplifiers". No subclass-per-potion required.
 *
 * @param id          fully-qualified id (e.g. {@code "wizards_and_beasts:wiggenweld_potion"})
 * @param displayName UI name, never null
 * @param color       ARGB color used for the bottle texture tint
 * @param effects     list of effect specs applied to the drinker
 * @param flavorText  optional one-line description; {@code null} if absent
 */
public record Brew(
        String id,
        String displayName,
        int color,
        List<EffectSpec> effects,
        String flavorText) {

    public Brew {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(effects, "effects");
        effects = List.copyOf(effects);
    }

    /**
     * Description of one mob effect applied by drinking the brew. Held as a
     * spec rather than a baked {@link MobEffectInstance} so duration scaling
     * (e.g. from {@code potion_potency} skill) can happen at consumption time.
     *
     * @param effect       resolved mob effect holder
     * @param baseDuration ticks before potency scaling
     * @param amplifier    0-based amplifier (vanilla convention)
     * @param ambient      ambient flag forwarded to the {@link MobEffectInstance}
     */
    public record EffectSpec(
            Holder<MobEffect> effect,
            int baseDuration,
            int amplifier,
            boolean ambient) {

        public EffectSpec {
            Objects.requireNonNull(effect, "effect");
            if (baseDuration < 1) {
                throw new IllegalArgumentException("baseDuration must be >= 1");
            }
        }

        /**
         * Builds a {@link MobEffectInstance} ready to be applied to a player,
         * with duration scaled by {@code potencyMultiplier}. Amplifier and
         * ambient flag are passed through unchanged.
         */
        public MobEffectInstance instantiate(float potencyMultiplier) {
            int duration = Math.max(1, Math.round(baseDuration * potencyMultiplier));
            return new MobEffectInstance(effect, duration, amplifier, ambient, true, true);
        }
    }
}
