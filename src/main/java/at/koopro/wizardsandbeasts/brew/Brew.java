package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
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
 * @param silverVariant id of the brew this one becomes when an Occamy eggshell is added to the
 *                      cauldron, or {@code null} if this brew is not silver-based
 */
public record Brew(
        String id,
        String displayName,
        int color,
        List<EffectSpec> effects,
        String flavorText,
        String silverVariant,
        List<BrewEffectEntry> components) {

    public Brew {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(components, "components");
        effects = List.copyOf(effects);
        components = List.copyOf(components);
    }

    /**
     * Six-argument form, for the brews that predate components.
     *
     * <p>Kept so adding the field did not have to touch every construction site and every test. A
     * brew built this way has an empty component list, and {@code BrewDefinition} is what turns a
     * legacy {@code effects} list into an {@code apply_effects} component at load time — so nothing
     * that reaches the drink path is ever componentless.
     */
    public Brew(String id, String displayName, int color, List<EffectSpec> effects,
                String flavorText, String silverVariant) {
        this(id, displayName, color, effects, flavorText, silverVariant, List.of());
    }

    /**
     * Five-argument form for a brew that is not silver-based, which is almost all of them.
     *
     * <p>Kept so that adding the silver field did not have to touch every existing construction site
     * and every test — a brew with no silver variant is the overwhelming default, and making callers
     * write {@code null} for it would have added noise everywhere to serve one mechanic.
     */
    public Brew(String id, String displayName, int color, List<EffectSpec> effects, String flavorText) {
        this(id, displayName, color, effects, flavorText, null, List.of());
    }

    /**
     * Whether an Occamy eggshell dropped into a cauldron brewing this would refine it.
     *
     * <p>"Silver-based" is defined by the brew declaring what it turns into, not by a flag or a name
     * match. A datapack that wants a new silver brew writes one field; nothing in code learns its
     * name.
     */
    public boolean isSilverBased() {
        return silverVariant != null && !silverVariant.isBlank();
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
