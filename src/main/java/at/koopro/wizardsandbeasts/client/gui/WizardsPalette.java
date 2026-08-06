package at.koopro.wizardsandbeasts.client.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

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
 *
 * <p>The constants below are the mod's one leather-and-brass chrome. {@link GuiSkin} is the
 * same vocabulary in five materials, and it reuses these tokens wherever a skin's colour
 * already lives here — see its own note.
 */
@NullMarked
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

    // ── Skins ──────────────────────────────────────────────────────────────

    /**
     * Compose {@code token} at {@code alpha}, keeping its hue and discarding its own alpha.
     *
     * <p>Every screen that wanted a translucent tint used to spell this as
     * {@code 0x33000000 | (SELECT & 0x00FFFFFF)}. That is two hex literals per tint, which is
     * how a class whose whole job is to remove literals ended up surrounded by them — the
     * masks are arithmetic, not colour, but a lint that counts {@code 0x…} cannot tell.
     *
     * @param alpha 0–255
     */
    public static int withAlpha(int token, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (token & 0x00FFFFFF);
    }

    /**
     * The five domain skins: one shared structure, five materials.
     *
     * <p>A skin is five roles and a sprite set. Panel geometry, spacing, type scale and widget
     * shapes are identical across all of them — {@link WizardsMetrics} owns those and does not
     * vary. What changes is what the chrome is <em>made of</em>.
     *
     * <p><strong>Reuse is the rule, not an optimisation.</strong> Six of the twenty-five role
     * colours landed within a few points of a token already on this class, and each of those
     * takes the token rather than declaring a near-duplicate beside it. That is the exact drift
     * this class's own javadoc was written to stop: a second {@code #3A2718} sitting nine points
     * from {@link #WELL} is how the mod grew four vocabularies for one brown. The reuses are
     * marked per-constant below, and {@code tools/gui_chrome.py}'s {@code SKINS} table carries
     * the same markers so the two can be diffed by eye.
     *
     * <p>{@link #MINISTRY_SKIN}'s frame is {@link #MINISTRY} exactly — the emblem's own purple,
     * to the byte. Ministry memos are pale violet in canon; the blue that reads as "Ministry" is
     * film atrium tiling and loses.
     */
    public enum GuiSkin {

        /** Scamander's case notes: kraft paper on an oiled canvas board, pencil, a leather strap. */
        FIELD_NOTEBOOK("field_notebook",
                0xFFD8CDB4,   // base
                0xFF3E4A46,   // frame
                INK,          // ink — reuse, 9.4 from #22201C
                SELECT,       // accent — reuse, 9.0 from #8A5A2B
                0xFF6E6A5C),  // muted

        /** Ministry interior: memo stock, the emblem's purple, gilt, stamp red. */
        MINISTRY_SKIN("ministry",
                PARCHMENT_SHADE,  // base — reuse, 10.2 from #E4DCC8
                MINISTRY,         // frame — reuse, exact
                0xFF14181B,
                0xFFC9A227,
                0xFF8C2B26),

        /** Astronomy tower: night void, indigo, silver leaf, a brass instrument. */
        STAR_CHART("star_chart",
                0xFF14172B, 0xFF232A52, 0xFFC7CEDB, 0xFFB08D3F, 0xFF7FA6D8),

        /**
         * Goblin ledger: oxblood leather, brass, ruled paper, gold.
         *
         * <p>Its base keeps its own value rather than collapsing onto {@link #PARCHMENT_SHADE}
         * eleven points away. Goblin ledger stock is not Ministry memo stock, and the vault is
         * the one screen where the two would otherwise be seen to be the same paper.
         */
        GOBLIN_LEDGER("goblin_ledger",
                0xFFE6DFC9, 0xFF4A1E1E, 0xFF1A1512, 0xFFD4AF37, 0xFF5A6E82),

        /** Ollivander's bench: worn wood, shellac, leather, brass calipers, pale shavings. */
        WORKBENCH("workbench",
                0xFFD9C49A,
                WELL,        // frame — reuse, 9.1 from #3A2718
                0xFF2A1F14, 0xFF9C7B32, 0xFF8A6A45);

        private final String dir;
        private final int base;
        private final int frame;
        private final int ink;
        private final int accent;
        private final int muted;

        GuiSkin(String dir, int base, int frame, int ink, int accent, int muted) {
            this.dir = dir;
            this.base = base;
            this.frame = frame;
            this.ink = ink;
            this.accent = accent;
            this.muted = muted;
        }

        /** The panel face — page, board, or void. */
        public int base() {
            return base;
        }

        /** The panel border. */
        public int frame() {
            return frame;
        }

        /** Body text on {@link #base()}. */
        public int ink() {
            return ink;
        }

        /** The filigree rule, selection, and small furniture. */
        public int accent() {
            return accent;
        }

        /** Secondary text: hints, units, unavailable entries. */
        public int muted() {
            return muted;
        }

        /**
         * A sprite on the GUI atlas, named the way {@code atlases/gui.json} stitches it.
         *
         * <p>Atlas sprite names are <em>not</em> texture paths: the {@code gui/sprites} source
         * declares an empty prefix, so {@code sprites/field_notebook/panel.png} is addressed as
         * {@code wizards_and_beasts:field_notebook/panel}. Passing a {@code textures/…} path to
         * {@link net.minecraft.client.gui.GuiGraphics#blitSprite} silently resolves to the
         * missing-texture sprite rather than failing.
         */
        public Identifier sprite(String element) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, dir + "/" + element);
        }
    }

    /** The state badge, shared by every skin and tinted per state. See {@code McStylePanel.drawStateBadge}. */
    public static final Identifier STATE_BADGE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "state/badge");

    private WizardsPalette() {
    }
}
