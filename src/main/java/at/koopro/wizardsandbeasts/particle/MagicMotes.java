package at.koopro.wizardsandbeasts.particle;

import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;

/**
 * The mod's own sparkle for a meaning, in place of the vanilla particles the style guide reserves
 * ({@code VISUAL_STYLE_GUIDE.md} §3): {@code HAPPY_VILLAGER} is "trade accepted", {@code WITCH} the witch,
 * {@code PORTAL} the Enderman, {@code END_ROD}/{@code ENCHANT} the end rod and the enchanting table. Each is a
 * tinted mod sprite, coloured from {@link MagicColours} so a sparkle means the same thing everywhere.
 *
 * <p>Safe on both sides: options only, no client classes.
 */
public final class MagicMotes {

    /** Silver, for silvering, the Veela and other moon-pale magic. */
    public static final int SILVER = 0xFFD8DCE8;
    /** Gold, for luck and the Time-Turner's hourglass: the light gilt tone. */
    public static final int GOLD = 0xFFEBC874;

    private MagicMotes() {}

    /** Healing, growth, a creature's goodwill. */
    public static SpellTintParticleOptions healing() {
        return ModParticles.tinted(SpellFamily.LIGHT, MagicColours.HEALING);
    }

    /** Light: a lit wand, a returning light, a bright brew. */
    public static SpellTintParticleOptions light() {
        return ModParticles.tinted(SpellFamily.LIGHT, MagicColours.LIGHT);
    }

    /** Magic at work: a charm, a transformation, learning, a teleport. */
    public static SpellTintParticleOptions magic() {
        return ModParticles.tinted(SpellFamily.ARCANE, MagicColours.MAGIC);
    }

    /** Dark magic: a blood pact, a curse. */
    public static SpellTintParticleOptions dark() {
        return ModParticles.tinted(SpellFamily.DARK, MagicColours.DARK_MAGIC);
    }

    public static SpellTintParticleOptions silver() {
        return ModParticles.tinted(SpellFamily.LIGHT, SILVER);
    }

    public static SpellTintParticleOptions gold() {
        return ModParticles.tinted(SpellFamily.LIGHT, GOLD);
    }
}
