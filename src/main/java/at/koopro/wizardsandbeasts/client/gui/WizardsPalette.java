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

    // ── The page ───────────────────────────────────────────────────────────
    //
    // Screens are parchment and ink (2026-09-23): the {@code gui/theme/} kit that
    // {@code tools/gui_parchment.py} generates is an aged sheet with a double iron-gall rule. The
    // leather above is not retired — it is what draws <em>over the world</em>: the spell and
    // ability wheels, the HUD, chat accents ({@code ChatPalette}). A light sheet over a bright
    // daytime sky is unreadable, so overlays keep the dark material and full screens take this one.
    //
    // Mirrors {@code gui_parchment.py}'s {@code DEFAULT}; same names, spelled out on both sides.

    /** The sheet itself. Backgrounds only. */
    public static final int PAGE = 0xFFE4D2A6;
    /** The paper one step lighter: raised chips, buttons. */
    public static final int PAGE_LIGHT = 0xFFEEE1BC;
    /** The paper one step darker: bar tracks, recessed wells drawn with {@code fill}. */
    public static final int PAGE_SHADE = 0xFFD5BE8C;
    /** Deepest paper tone: rules between rows, empty pips. */
    public static final int PAGE_DEEP = 0xFFB99B63;
    /** A selected row: the sheet washed with gilt. */
    public static final int PAGE_SELECT = 0xFFDFC381;
    /** Body text on the page. Iron-gall brown-black, never pure black. 11:1 on {@link #PAGE}. */
    public static final int PAGE_INK = 0xFF2E1F16;
    /** Secondary text: labels, units, hints. 6:1 on {@link #PAGE}. */
    public static final int PAGE_INK_2 = 0xFF5C4330;
    /** Disabled and faint text. Not AA — use only for text that may recede. */
    public static final int PAGE_INK_3 = 0xFF8A6E52;
    /**
     * Section headings: rubrication, the red ink a scribe used for headings. 6:1 on {@link #PAGE}.
     * Wax and rubric are the same red on purpose — one red on the page, not two.
     */
    public static final int PAGE_RUBRIC = 0xFF8E2320;
    /** Good news in ink — a beneficial effect, an eligible check. 5.5:1 on {@link #PAGE}. */
    public static final int PAGE_GOOD = 0xFF2F5E1E;
    /** Bad news in ink — a harmful effect, a failed check. Darker than the rubric so the two differ. */
    public static final int PAGE_BAD = 0xFF7A1A12;
    /** Gilt is furniture (bars, pips, ticks), never body text: it is under 4:1 on the page. */
    public static final int GILT_DARK = 0xFF8F6522;
    public static final int GILT = 0xFFC9973A;
    public static final int GILT_LIGHT = 0xFFEBC874;
    /** Sealing wax — the scroll thumb, the seal, a warning that is not yet an error. */
    public static final int WAX = 0xFF8E2320;

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
     * <p>It is load-bearing rather than tidy. Every material is <em>light</em>, and the leather
     * colours above assume a dark panel: {@link #TEXT} on {@link #WORKBENCH}'s face is 1.4 : 1, and
     * the wand trial once drew its labels in {@link #BRASS_HI} at 1.35 : 1. A screen that
     * takes a skin must take that skin's {@link #ink()} with it, and before this there was nothing
     * to take. {@code UiContrast.readableOn} is the escape hatch for a colour that carries meaning
     * and so cannot simply be replaced — a coin's gold, a spell family's hue.
     *
     * <p>Since 2026-09-23 every material is a <em>paper</em>: the construction (torn edge, double
     * ink rule, gilt, wax seal) is shared and a skin changes only stock, ink, gilt and wax, so
     * {@link #ink()} is dark on every one of them.
     *
     * <p>Keep in step with {@code gui_parchment.py}'s {@code SKINS}: base = its {@code paper},
     * frame and ink = its {@code ink}, accent = its {@code gilt}, muted = its {@code ink_2}. Both
     * spell the values out rather than deriving them from the other, so the two can be diffed by eye.
     */
    public enum GuiSkin {
        /** Scamander's case notes: kraft notebook paper, graphite, a leather strap. */
        FIELD_NOTEBOOK("field_notebook", 0xFFCDBC95, 0xFF2B2B2B, 0xFF2B2B2B, 0xFFC9973A, 0xFF555350),
        /** Ministry memo stock, the emblem's purple ink ({@link #MINISTRY}'s family), gold. */
        MINISTRY_MEMO("ministry", 0xFFEAE1C6, 0xFF2A1433, 0xFF2A1433, 0xFFC9973A, 0xFF4E3558),
        /** An engraved celestial atlas: blue-grey vellum, indigo ink, silver leaf. */
        STAR_CHART("star_chart", 0xFFD2D5D0, 0xFF18204A, 0xFF18204A, 0xFFA4AEBE, 0xFF3A4677),
        /** Goblin ledger: ruled ledger paper, oxblood ink, gold. */
        GOBLIN_LEDGER("goblin_ledger", 0xFFE4D2A6, 0xFF2A1512, 0xFF2A1512, 0xFFD4AF37, 0xFF5A2A22),
        /** Ollivander's pattern paper: pale and shaving-coloured, brown ink, brass. */
        WORKBENCH("workbench", 0xFFE0CFA9, 0xFF2E2012, 0xFF2E2012, 0xFFC9973A, 0xFF5E4526),
        /** The Marauder's Map: old, dirty, much-folded parchment, drawn by hand. */
        MARAUDERS_MAP("marauders_map", 0xFFDAC291, 0xFF3A2414, 0xFF3A2414, 0xFFC9973A, 0xFF6A4A2E),
        /** The Pensieve: silvered vellum, aubergine ink, the silver-blue light of a memory. */
        PENSIEVE("pensieve", 0xFFD8D6DE, 0xFF261C36, 0xFF261C36, 0xFFA8B6D6, 0xFF4A3F5E),
        /**
         * The Floo grate: a soot-stained sheet, soot ink, green fire.
         *
         * <p>The accent is {@code FlooCues.EMERALD} to the byte. It is not <em>read</em> from there
         * — that constant is common code and this is a client palette, and the rule in this file is
         * that a value is spelled rather than derived so the two copies can be diffed by eye — but
         * the tie is deliberate: on the Floo screen the green is the one colour carrying meaning
         * rather than theme, so the material is built around it instead of beside it.
         */
        HEARTH("hearth", 0xFFC6B99C, 0xFF1E1A18, 0xFF1E1A18, 0xFF21B342, 0xFF3E3830);

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
         * <p>The ink laid thin. It clears AA on every paper today, but it is the material's second
         * voice rather than a promise: use it for text that may recede, and {@link #ink()} for text
         * that must be read.
         */
        public int muted() {
            return muted;
        }
    }

    private WizardsPalette() {
    }
}
