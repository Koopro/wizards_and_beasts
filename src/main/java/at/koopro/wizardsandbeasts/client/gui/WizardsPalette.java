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

    /**
     * The materials the shared chrome is cut from, and the only place their colours are spelled.
     *
     * <p>{@code tools/gui_chrome.py} generates eleven sprites per skin from its own {@code SKINS}
     * table and has claimed since it was written that "{@code WizardsPalette.GuiSkin} mirrors it".
     * It did not exist. So the two screens that adopted a skin each hand-copied the numbers into a
     * class of their own — {@code SkillTreeChartTextures.CHART_INK} is {@link #STAR_CHART}'s
     * {@link #ink()} retyped — which is exactly the drift the rest of this file exists to stop, one
     * level down.
     *
     * <p>It is load-bearing rather than tidy. Four of the six materials are <em>light</em>, and
     * every colour above assumes the dark leather panel: {@link #TEXT} on {@link #WORKBENCH}'s face
     * is 1.4 : 1, and the wand trial drew its labels in {@link #BRASS_HI} at 1.35 : 1. A screen that
     * takes a skin must take that skin's {@link #ink()} with it, and before this there was nothing
     * to take. {@code UiContrast.readableOn} is the escape hatch for a colour that carries meaning
     * and so cannot simply be replaced — a coin's gold, a spell family's hue.
     *
     * <p>Keep in step with {@code gui_chrome.py}'s {@code SKINS}. Both spell the values out rather
     * than deriving them from the other, so the two can be diffed by eye.
     */
    public enum GuiSkin {
        /** Scamander's case notes: kraft paper on an oiled canvas board, pencil, a leather strap. */
        FIELD_NOTEBOOK("field_notebook", 0xFFD8CDB4, 0xFF3E4A46, INK, SELECT, 0xFF6E6A5C),
        /** Ministry interior. The frame is {@link #MINISTRY} to the byte; memos are pale violet. */
        MINISTRY_MEMO("ministry", PARCHMENT_SHADE, MINISTRY, 0xFF14181B, 0xFFC9A227, 0xFF8C2B26),
        /** Astronomy tower: night void, indigo, silver leaf, a brass instrument. */
        STAR_CHART("star_chart", 0xFF14172B, 0xFF232A52, 0xFFC7CEDB, 0xFFB08D3F, 0xFF7FA6D8),
        /** Goblin ledger: oxblood leather, brass, ruled paper, gold. */
        GOBLIN_LEDGER("goblin_ledger", 0xFFE6DFC9, 0xFF4A1E1E, 0xFF1A1512, 0xFFD4AF37, 0xFF5A6E82),
        /** Ollivander's bench: worn wood, shellac, leather, brass calipers, pale shavings. */
        WORKBENCH("workbench", 0xFFD9C49A, WELL, 0xFF2A1F14, 0xFF9C7B32, 0xFF8A6A45),
        /** The Marauder's Map: aged parchment in a scuffed leather portfolio, pocket-worn brass. */
        MARAUDERS_MAP("marauders_map", 0xFFE3D6AE, 0xFF4A3524, 0xFF3A2E24, 0xFFB08A4A, 0xFF6B5A46),
        /** The Pensieve: dark wet stone with an aubergine cast, and the light a memory gives off. */
        PENSIEVE("pensieve", 0xFF221C2E, 0xFF4A3F5E, 0xFFDCE4F0, 0xFFC8D4E8, 0xFF8A87A8),
        /**
         * The Floo grate: soot-black stone, warm soot, green fire.
         *
         * <p>The accent is {@code FlooCues.EMERALD} to the byte. It is not <em>read</em> from there
         * — that constant is common code and this is a client palette, and the rule in this file is
         * that a value is spelled rather than derived so the two copies can be diffed by eye — but
         * the tie is deliberate: on the Floo screen the green is the one colour carrying meaning
         * rather than theme, so the material is built around it instead of beside it.
         */
        HEARTH("hearth", 0xFF141618, 0xFF2A2422, 0xFFE4E0D8, 0xFF21B342, 0xFF8A8A82);

        private final String folder;
        private final int base;
        private final int frame;
        private final int ink;
        private final int accent;
        private final int muted;

        GuiSkin(String folder, int base, int frame, int ink, int accent, int muted) {
            this.folder = folder;
            this.base = base;
            this.frame = frame;
            this.ink = ink;
            this.accent = accent;
            this.muted = muted;
        }

        /** The {@code gui/sprites/<folder>/} directory this skin's art lives in. */
        public String folder() {
            return folder;
        }

        /** The panel face. Backgrounds only — never text, which is what {@link #ink()} is for. */
        public int base() {
            return base;
        }

        /** The border. */
        public int frame() {
            return frame;
        }

        /** Body text on this material, and the only colour guaranteed to be readable on it. */
        public int ink() {
            return ink;
        }

        /** The filigree rule and small furniture: bars, thumbs, the selected-row tint. */
        public int accent() {
            return accent;
        }

        /**
         * Secondary text: hints, units, disabled entries.
         *
         * <p>Not guaranteed to clear AA against {@link #base()} — it is the material's own second
         * voice, and on {@link #WORKBENCH} it lands at 2.9 : 1. Use it for text that may recede,
         * and {@link #ink()} for text that must be read.
         */
        public int muted() {
            return muted;
        }
    }

    private WizardsPalette() {
    }
}
