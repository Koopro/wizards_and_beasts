package at.koopro.wizardsandbeasts.client.gui;

/**
 * Shared client UI constants for WizardsAndBeastsMod screens/overlays.
 */
public final class WizardsAndBeastsUiTokens {

    private WizardsAndBeastsUiTokens() {}

    public static final class SpellMenu {
        private SpellMenu() {}

        public static final int PANEL_WIDTH = 310;
        public static final int PANEL_HEIGHT = 250;
        public static final int LEFT_WIDTH = 160;

        public static final int TOP_PADDING = 4;
        public static final int LEFT_PADDING = 4;
        public static final int SEARCH_HEIGHT = 14;
        public static final int SEARCH_TO_LIST_GAP = 4;
        public static final int LIST_ROW_SPACING = 18;
        public static final int LIST_BUTTON_HEIGHT = 16;
        public static final int LIST_BUTTON_SIDE_PADDING = 8;

        public static final int HEADER_Y_OFFSET = 6;
        public static final int EMPTY_LIST_TEXT_Y_OFFSET = 4;

        public static final int SLOT_CENTER_X_OFFSET = 75;
        public static final int SLOT_CENTER_Y_OFFSET = 80;
        public static final int SLOT_BUTTON_SIZE = 32;
        public static final int SLOT_SPACING = 38;

        public static final int COOLDOWN_X_OFFSET = 52;
        public static final int PROF_X_OFFSET = 16;
        public static final int ENTRY_TEXT_Y_OFFSET = 4;
        public static final int CATEGORY_X_OFFSET = 2;

        public static final int SELECTED_INFO_BASE_Y = 140;
        public static final int SELECTED_CATEGORY_Y = 12;
        public static final int SELECTED_COOLDOWN_Y = 24;
        public static final int SELECTED_DAMAGE_Y = 36;
        public static final int SELECTED_PROF_WITH_DAMAGE_Y = 48;
        public static final int SELECTED_PROF_NO_DAMAGE_Y = 36;
        public static final int SELECTED_REQ_Y = 14;
        public static final int ASSIGN_HINT_CENTER_X = 75;
        public static final int ASSIGN_HINT_BOTTOM_OFFSET = 16;

        public static final int COOLDOWN_COLOR = 0xFFFFAA44;
        public static final int PROF_PROFICIENT = 0xFFFFFF44;
        public static final int PROF_MASTERED = 0xFFFFD700;
    }

    public static final class BeamDebug {
        private BeamDebug() {}

        public static final int PANEL_WIDTH = 430;
        public static final int PANEL_HEIGHT = 380;

        public static final int COLOR_HIGHLIGHT_TOP = 0xFF555555;
        public static final int COLOR_SHADOW_BOTTOM = 0xFF2D2D2D;
        public static final int COLOR_TITLE = 0xFFFFFFFF;
        public static final int COLOR_LABEL = 0xFFA0A0A0;
        public static final int COLOR_ACTIVE = 0xFFFFFF55;
        public static final int COLOR_DIVIDER = 0xFF373737;

        public static final int LEFT_X = 10;
        public static final int LEFT_WIDTH = 125;
        public static final int LEFT_TO_RIGHT_GAP = 15;
        public static final int RIGHT_WIDTH = 260;

        public static final int PRESET_START_Y = 32;
        public static final int PRESET_BUTTON_X_OFFSET = 14;
        public static final int PRESET_BUTTON_Y_SPACING = 18;
        public static final int PRESET_BUTTON_HEIGHT = 16;

        public static final int LAYER_SECTION_GAP = 18;
        public static final int LAYER_BUTTON_Y_SPACING = 22;
        public static final int LAYER_BUTTON_HEIGHT = 18;
        public static final int LAYER_COUNT = 3;

        public static final int SLIDER_HEIGHT = 20;
        public static final int LAYER_SLIDER_START_Y = 40;
        public static final int LAYER_SLIDER_GAP = 26;
        public static final int RESET_LAYER_WIDTH = 100;
        public static final int RESET_LAYER_HEIGHT = 20;
        public static final int RESET_LAYER_Y_EXTRA = 4;

        public static final int GLOBAL_START_Y = 246;
        public static final int GLOBAL_SLIDER_GAP = 24;

        public static final int BOTTOM_BUTTONS_BOTTOM_OFFSET = 26;
        public static final int BOTTOM_BUTTON_WIDTH = 80;
        public static final int BOTTOM_BUTTON_HEIGHT = 20;
        public static final int RESET_ALL_X_OFFSET = -130;
        public static final int CLOSE_X_OFFSET = 50;

        public static final int TITLE_Y = 8;
        public static final int VDIVIDER_X = 143;
        public static final int VDIVIDER_Y = 25;
        public static final int VDIVIDER_BOTTOM_OFFSET = 30;

        public static final int PRESET_LABEL_Y = 22;
        public static final int PRESET_DOT_X = 12;
        public static final int PRESET_DOT_WIDTH = 10;
        public static final int PRESET_DOT_HEIGHT = 8;
        public static final int PRESET_DOT_Y_OFFSET = 4;

        public static final int LAYER_LABEL_EXTRA = 6;
        public static final int RIGHT_TITLE_Y = 28;
        public static final int SWATCH_X_GAP = 8;
        public static final int SWATCH_Y = 27;
        public static final int SWATCH_WIDTH = 14;
        public static final int SWATCH_HEIGHT = 10;

        public static final int GLOBAL_DIVIDER_Y = 232;
        public static final int GLOBAL_LABEL_Y = 236;
    }

    public static final class SkillTree {
        private SkillTree() {}

        public static final int PANEL_WIDTH = 412;
        public static final int PANEL_HEIGHT = 274;

        public static final int FOOTER_HEIGHT = 20;

        public static final int VIEWPORT_X = 10;
        /**
         * 34, not 48. The header carries a title, a rule at y=20 and the vocation button, all of
         * which finish by y=28; the remaining 20px were a band of empty panel above the chart.
         */
        public static final int VIEWPORT_Y = 34;
        public static final int VIEWPORT_WIDTH = 392;
        /**
         * The window is a nine-slice with an 8px frame now, and the footer is a recessed strip
         * inset from that frame rather than a {@code fill} flush with the panel's bottom edge — at
         * the old 204 the viewport's own frame ran underneath the footer. The budget down the
         * panel is exact: 34 header + 208 viewport + 4 gap + 20 footer + 8 pad = 274.
         */
        public static final int VIEWPORT_HEIGHT = 208;

        public static final int FOOTER_RIGHT_PAD = 8;
        public static final int FOOTER_TEXT_X = 12;
        public static final int FOOTER_MIN_GAP = 14;

        public static final int TOOLTIP_WIDTH = 248;
        public static final int TOOLTIP_OFFSET_X = 12;
        public static final int TOOLTIP_OFFSET_Y = 10;

        // ── Star-chart skin ────────────────────────────────────────────────
        //
        // Added with the texture pass that ended this screen's hand-drawn chrome. The tokens
        // above are the older leather palette; the chart wears `gui/sprites/star_chart/` and
        // only reaches for the ones that still apply (TITLE_Y, VIEWPORT_*, TOOLTIP_*, STATUS_*).

        /**
         * Inset from the panel edge to the header rule and the footer strip.
         *
         * <p>8 because the nine-slice border is 8 — anything smaller draws content on top of the
         * frame art rather than inside it.
         */
        public static final int CHROME_PAD = 8;
        /** Seal rivets: inset from each top corner of the panel. */
        public static final int SEAL_INSET = 5;

        /** Points bar in the footer: earned against the campaign cap. */
        public static final int POINTS_BAR_HEIGHT = 4;
        public static final int POINTS_BAR_WIDTH = 96;
        public static final int POINTS_BAR_GAP = 8;

        /** Viewport controls, bottom-right inside the well. Margin clears the well's own 8px frame. */
        public static final int CONTROL_SIZE = 18;
        public static final int CONTROL_GAP = 3;
        public static final int CONTROL_MARGIN = 10;
        /** Vocation button, top-right in the header band. Wide enough for "Vocation: Wandlore Master". */
        public static final int VOCATION_BUTTON_W = 140;
        public static final int VOCATION_BUTTON_H = 16;

        /** Level pips under a multi-level node, and the zoom below which they are hidden. */
        public static final int PIP_DRAW_SIZE = 5;
        public static final int PIP_SPACING = 6;
        public static final int PIP_GAP = 3;

        /** Constellation label: glyph size and its clearance from the text. */
        public static final int LABEL_GLYPH_SIZE = 12;
        public static final int LABEL_GLYPH_GAP = 3;

        /** Ley-line stroke weights, by edge state. */
        public static final int LEY_ALLOCATED_STROKE = 3;
        public static final int LEY_LOCKED_STROKE = 1;
    }
}
