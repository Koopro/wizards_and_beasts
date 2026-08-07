package at.koopro.wizardsandbeasts.client.gui;

/**
 * The one palette every screen in this mod draws from.
 *
 * <p>Sampled from the wand HUD, which is the visual reference: warm tooled leather with gold
 * filigree. These are the Java-drawn counterparts of the textures {@code tools/gui_chrome.py}
 * generates, and the two have to agree or a fill sits on a panel it does not belong to — the
 * constant names deliberately mirror that file's so the pairing stays obvious.
 *
 * <p>This exists because it did not. The values lived in {@code BestiaryColors}, scoped to one
 * screen, so every other screen invented its own approximation of the same leather and drifted:
 * the wand trial had ended up on a cold blue-lavender scheme that shared no hue with anything
 * else in the mod. Import this rather than adding another literal.
 *
 * <p>Semantic colours (danger red, Floo green, Avada's green) are deliberately <em>not</em> here.
 * Those carry meaning rather than theme and belong with the feature that means them.
 */
public final class WizardsPalette {

    // ── Surfaces, dark to light ────────────────────────────────────────────
    /** The dark the panels float on. */
    public static final int INK = 0xFF1E1A22;
    /** Recessed leather: wells, scroll tracks, inset lists. */
    public static final int WELL = 0xFF3A2621;
    /** The HUD's field colour — the default panel face. */
    public static final int PLATE = 0xFF663A31;
    /** One step up from the field: rows, cards, chips. */
    public static final int PLATE_2 = 0xFF71443A;
    /** Raised leather: headers and rails. */
    public static final int RAIL = 0xFF8A5240;
    /** A selected row or an active tab. */
    public static final int SELECT = 0xFF8A5A34;

    // ── Gold filigree ──────────────────────────────────────────────────────
    /** Shadowed gold, for the seat under a bright rule. */
    public static final int LINE = 0xFFA4764A;
    /** Lit leather edge. */
    public static final int EDGE_HI = 0xFFC08A5A;
    /** The filigree itself. */
    public static final int BRASS = 0xFFDBA86D;
    /** Its highlight, and the brightest thing on a panel. */
    public static final int BRASS_HI = 0xFFF5E4B0;
    /** Scroll thumbs and other small brass furniture. */
    public static final int THUMB = 0xFFC9A06B;

    // ── Text ───────────────────────────────────────────────────────────────
    /** Body text on leather. */
    public static final int TEXT = 0xFFF3E6D2;
    /** Secondary text: hints, units, disabled entries. */
    public static final int TEXT_DIM = 0xFFC2A78F;

    // ── Indicators ─────────────────────────────────────────────────────────
    public static final int PIP_ON = 0xFFF5E4B0;
    public static final int PIP_OFF = 0xFF5E3A2E;

    // ── Ministry of Magic ──────────────────────────────────────────────────
    //
    // The mod's *second* brand, and a deliberate one. {@link #MINISTRY} is sampled straight
    // off handbook/emblem.png — it is that file's dominant colour to the byte — so anything
    // speaking for the Ministry wears purple on parchment rather than the wand HUD's
    // leather. The Ministry Handbook is the reference.
    //
    // Listed here so it reads as a brand rather than as drift: a hue audit that only knows
    // about the leather family flags these as strays and invites someone to "correct" them.

    /** The emblem's own purple. */
    public static final int MINISTRY = 0xFF3E1F47;
    /** Binding and cover edge. */
    public static final int MINISTRY_DARK = 0xFF221328;
    /** Lit edge of the cover. */
    public static final int MINISTRY_LIGHT = 0xFF5C3E66;

    /** Aged page stock the Ministry prints on. */
    public static final int PARCHMENT = 0xFFEFE7CF;
    /** Its shaded side, for the gutter and page edges. */
    public static final int PARCHMENT_SHADE = 0xFFE6DCBE;
    /** Body text on parchment — brown, never black. */
    public static final int PARCHMENT_INK = 0xFF3A2E24;

    private WizardsPalette() {
    }
}
