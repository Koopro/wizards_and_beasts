package at.koopro.wizardsandbeasts.spell.core;

/**
 * The colours magic means something by. Every effect that tints a particle, a beam or a spell
 * picks from here, so the same colour always says the same thing: violet is ordinary magic,
 * soft green is healing, sickly green is poison, silver-blue is protection.
 *
 * <p>Opaque ARGB. See {@code documentation/VISUAL_STYLE_GUIDE.md}, section 3 — the table there
 * and these constants are the same list; change them together.
 */
public final class MagicColours {

    private MagicColours() {}

    /** Ordinary magic: charms, transfiguration, movement and teleport. */
    public static final int MAGIC = 0xFF9A6BFF;
    /** Curses and dark creatures. Deep violet-black, never pure black. */
    public static final int DARK_MAGIC = 0xFF5A1E6E;
    /** The Killing Curse alone. */
    public static final int DEATH = 0xFF3CFF6A;
    /** Healing, regeneration, restoring. Soft, pale green — never vanilla's villager emerald. */
    public static final int HEALING = 0xFF8FE3A0;
    /** Fire and heat. */
    public static final int FIRE = 0xFFFF6A1E;
    /** Poison, venom and disease. Sickly yellow-green. */
    public static final int POISON = 0xFF8AA02A;
    /** Shields, wards and the Patronus. Silver-blue. */
    public static final int PROTECTION = 0xFF9CCBFF;
    /** Corruption, the Obscurus and soul damage. Bruised plum. */
    public static final int CORRUPTION = 0xFF4E2440;
    /** Light and warmth: Lumos, bioluminescence, glamour. Pale gold. */
    public static final int LIGHT = 0xFFFFE89A;
    /** Stunning and disarming — the scarlet jet of a duel. */
    public static final int STUN = 0xFFE8262E;
    /** Cold and ice. */
    public static final int ICE = 0xFF9ADCFF;
    /** Water. */
    public static final int WATER = 0xFF3A8CFF;
    /** Lightning and storm. */
    public static final int ELECTRIC = 0xFFFFF07A;
}
