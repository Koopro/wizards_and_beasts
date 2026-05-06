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
        public static final int MAX_VISIBLE = 11;

        public static final int PANEL_FILL = 0xCC1A1A2E;
        public static final int PANEL_BORDER = 0xFF444466;
        public static final int TITLE_COLOR = 0xFFFFD700;
        public static final int DIVIDER_COLOR = 0xFF444466;

        public static final int TOP_PADDING = 4;
        public static final int LEFT_PADDING = 4;
        public static final int SEARCH_HEIGHT = 14;
        public static final int SEARCH_TO_LIST_GAP = 4;
        public static final int LIST_ROW_SPACING = 18;
        public static final int LIST_BUTTON_HEIGHT = 16;
        public static final int LIST_BUTTON_SIDE_PADDING = 8;

        public static final int HEADER_Y_OFFSET = 6;
        public static final int DIVIDER_Y_OFFSET = 20;
        public static final int DIVIDER_BOTTOM_OFFSET = 5;
        public static final int EMPTY_LIST_TEXT_Y_OFFSET = 4;

        public static final int SLOT_CENTER_X_OFFSET = 75;
        public static final int SLOT_CENTER_Y_OFFSET = 80;
        public static final int SLOT_BUTTON_SIZE = 32;
        public static final int SLOT_SPACING = 38;
        public static final int SLOT_ABBR_LEN = 5;

        public static final int SELECT_HIGHLIGHT_LEFT_OFFSET = 1;
        public static final int SELECT_HIGHLIGHT_TOP_OFFSET = 1;
        public static final int SELECT_HIGHLIGHT_BOTTOM_OFFSET = 1;
        public static final int SELECT_HIGHLIGHT_RIGHT_OFFSET = 7;
        public static final int COOLDOWN_X_OFFSET = 52;
        public static final int PROF_X_OFFSET = 16;
        public static final int ENTRY_TEXT_Y_OFFSET = 4;
        public static final int CATEGORY_X_OFFSET = 2;

        public static final int SCROLL_ARROW_WIDTH = 12;
        public static final int SCROLL_ARROW_HEIGHT = 10;
        public static final int SCROLL_ARROW_HALF_WIDTH = 6;
        public static final int SCROLL_UP_BUTTON_Y = 14;
        public static final int SCROLL_DOWN_BUTTON_BOTTOM_OFFSET = 20;
        public static final int SCROLL_UP_INDICATOR_Y = 18;
        public static final int SCROLL_DOWN_INDICATOR_BOTTOM_OFFSET = 12;
        public static final int SCROLL_COLOR = 0xFF888888;

        public static final int SELECTED_INFO_X_OFFSET = 10;
        public static final int SELECTED_INFO_BASE_Y = 140;
        public static final int SELECTED_CATEGORY_Y = 12;
        public static final int SELECTED_COOLDOWN_Y = 24;
        public static final int SELECTED_DAMAGE_Y = 36;
        public static final int SELECTED_PROF_WITH_DAMAGE_Y = 48;
        public static final int SELECTED_PROF_NO_DAMAGE_Y = 36;
        public static final int SELECTED_REQ_Y = 14;
        public static final int ASSIGN_HINT_CENTER_X = 75;
        public static final int ASSIGN_HINT_BOTTOM_OFFSET = 16;

        public static final int EMPTY_TEXT_COLOR = 0xFFAAAAAA;
        public static final int SELECT_HIGHLIGHT_COLOR = 0x55FFFFFF;
        public static final int COOLDOWN_COLOR = 0xFFFFAA44;
        public static final int PROF_DEFAULT_DARK = 0xFF666666;
        public static final int PROF_NOVICE_TEXT = 0xFFAAAAAA;
        public static final int PROF_PROFICIENT = 0xFFFFFF44;
        public static final int PROF_MASTERED = 0xFFFFD700;
        public static final int CATEGORY_TEXT = 0xFF888888;
        public static final int REQUIREMENT_TEXT = 0xFF777777;
        public static final int ASSIGN_HINT_COLOR = 0xFF88FF88;
    }

    public static final class BeamDebug {
        private BeamDebug() {}

        public static final int PANEL_WIDTH = 430;
        public static final int PANEL_HEIGHT = 380;

        public static final int COLOR_BG = 0xC8101010;
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

    public static final class SpellDiamond {
        private SpellDiamond() {}

        public static final int SLOT_SIZE = 26;
        public static final int DIAMOND_SPACING = 30;
        public static final int CENTER_X_OFFSET = 100;
        public static final int CENTER_Y_BOTTOM_OFFSET = 52;

        public static final int SLOT_BG = 0x96130D1F;
        public static final int SLOT_ACTIVE = 0xBDA86B1A;
        public static final int SLOT_BORDER_ACTIVE = 0xFFFFD277;
        public static final int SLOT_BORDER_INACTIVE = 0xFF5A476B;
        public static final int SLOT_EMPTY_TEXT = 0x88E2D9F1;
        public static final int SPELL_TEXT = 0xFFEFE7FF;

        public static final int COOLDOWN_OVERLAY_COLOR = 0xAA17112A;
        public static final int COOLDOWN_TEXT_COLOR = 0xFFFFD385;
        public static final int SPELL_ABBR_LEN = 4;
        public static final int ACTIVE_NAME_Y_GAP = 6;
    }

    public static final class HeritageSelection {
        private HeritageSelection() {}

        public static final int PANEL_WIDTH = 392;
        public static final int PANEL_HEIGHT = 308;

        public static final int COLOR_BG = 0xF02A1F12;
        public static final int COLOR_BORDER_LIGHT = 0xFFE3C88B;
        public static final int COLOR_BORDER_DARK = 0xFF3C2A14;
        public static final int COLOR_INNER_BG = 0xE8D0BA8A;
        public static final int COLOR_CARD_BG = 0xE8C6AB74;
        public static final int COLOR_CYAN = 0xFFC69A44;
        public static final int COLOR_TEXT = 0xFF2A2013;
        public static final int COLOR_DIM = 0xFF3A2B17;
        public static final int COLOR_GREEN = 0xFF275F31;
        public static final int COLOR_RED = 0xFF7A1E1E;
        public static final int COLOR_INK = 0xFF1B130A;
        public static final int COLOR_ACCENT = 0xFF5D3F1E;
        public static final int COLOR_DIVIDER = 0xAA87683A;
        public static final int COLOR_PARCHMENT_BRIGHT = 0xF0E1CC9D;
        public static final int COLOR_PARCHMENT_DARK = 0xE8AE8A58;

        public static final int OUTER_PAD = 7;
        public static final int INNER_PAD = 8;
        public static final int CARD_X = 16;
        public static final int CARD_Y = 14;
        public static final int CARD_W = 360;
        public static final int CARD_H = 260;

        public static final int HEADER_X = 23;
        public static final int HEADER_Y = 20;
        public static final int HEADER_W = 344;
        public static final int HEADER_H = 86;
        public static final int TYPE_ICON_X = 31;
        public static final int TYPE_ICON_Y = 31;
        public static final int TYPE_ICON_SIZE = 58;
        public static final int HEADER_TEXT_GAP = 12;
        public static final int TITLE_Y = 29;
        public static final int HEADER_DESC_Y = 45;
        public static final int HEADER_DESC_MAX_LINES = 3;

        public static final int BODY_X = 26;
        public static final int BODY_START_Y = 122;
        public static final int LINE_H = 14;
        public static final int INFO_RIGHT_PAD = 24;

        public static final int ARROW_BUTTON_W = 24;
        public static final int ARROW_BUTTON_H = 58;
        public static final int ARROW_GAP = 10;
        public static final int ARROW_Y_OFFSET = 120;

        public static final int SELECT_BUTTON_W = 112;
        public static final int SELECT_BUTTON_H = 22;
        public static final int SELECT_BUTTON_BOTTOM_GAP = 8;

        public static final int LIST_X = 20;
        public static final int SUBTYPE_LIST_Y = 168;
        public static final int LIST_BUTTON_HEIGHT = 22;
        public static final int LIST_BUTTON_GAP = 4;
        public static final int PANEL_SIDE_PADDING = 40;
        public static final int BACK_BUTTON_WIDTH = 72;
        public static final int BACK_BUTTON_HEIGHT = 22;
        public static final int BOTTOM_BUTTON_OFFSET = 32;
        public static final int CONFIRM_BUTTON_WIDTH = 90;
        public static final int CONFIRM_BUTTON_HEIGHT = 22;
        public static final int CONFIRM_BUTTON_GAP = 8;
        public static final int SUBTYPE_ICON_X = 26;
        public static final int SUBTYPE_ICON_Y = 27;
        public static final int CONFIRM_ICON_X = 26;
        public static final int CONFIRM_ICON_Y = 27;

        public static final int DIVIDER_LEFT = 16;
        public static final int DIVIDER_RIGHT = 16;
        public static final int DIVIDER_Y = 120;
    }

    public static final class SkillTree {
        private SkillTree() {}

        public static final int PANEL_WIDTH = 412;
        public static final int PANEL_HEIGHT = 274;

        public static final int HEADER_HEIGHT = 24;
        public static final int FOOTER_HEIGHT = 20;
        public static final int TITLE_Y = 8;
        public static final int TITLE_COLOR = 0xFFF1E0B2;
        public static final int SUBTEXT_COLOR = 0xFFAB9E86;
        public static final int PANEL_INNER_FILL = 0xE8E2D4BB;
        public static final int HEADER_GOLD_LINE = 0xCC8A6A2A;

        public static final int BACKDROP_A = 0xFF0E1020;
        public static final int BACKDROP_B = 0xFF0B0C14;
        public static final int PANEL_FILL = 0xEE2A2418;
        public static final int HEADER_FILL = 0xEE3A311F;
        public static final int BORDER_COLOR = 0xFF6D5838;

        public static final int TABS_X = 10;
        public static final int TABS_Y = 28;
        public static final int TAB_WIDTH = 76;
        public static final int TAB_HEIGHT = 16;
        public static final int TAB_GAP = 3;
        public static final int TAB_TEXT_Y = 4;
        public static final int TAB_INACTIVE = 0xCC3D3122;
        public static final int TAB_ACTIVE = 0xEEDCCAA6;
        public static final int TAB_HOVER = 0xCC55432E;
        public static final int TAB_TEXT = 0xFFD0BF97;
        public static final int TAB_TEXT_ACTIVE = 0xFF402E1E;
        public static final int TAB_ACTIVE_BORDER = 0xFF9A7942;
        public static final int TAB_TOP_HIGHLIGHT = 0x66F3DFB2;
        public static final int TAB_HOVER_GLOW = 0x228F7040;
        public static final int TAB_ACTIVE_PULSE = 0x33FFF2C4;

        public static final int VIEWPORT_X = 10;
        public static final int VIEWPORT_Y = 48;
        public static final int VIEWPORT_WIDTH = 392;
        public static final int VIEWPORT_HEIGHT = 204;
        public static final int VIEWPORT_BG = 0xEE201910;

        public static final int GRID_SPACING = 24;
        public static final int GRID_LINE = 0x2A5A4A31;

        public static final int NODE_SIZE = 24;
        public static final int NODE_X_SPACING = 64;
        public static final int NODE_Y_SPACING = 46;
        public static final int NODE_LEVEL_Y = 15;
        public static final int NODE_BORDER = 0xFF7D6442;
        public static final int NODE_HOVER_BORDER = 0xFFF0CB7E;
        public static final int NODE_SELECTED_BORDER = 0xFFFFF0A2;
        public static final int NODE_HOVER_GLOW = 0x336D542E;
        public static final int NODE_SELECTED_GLOW = 0x448B6A35;
        public static final int NODE_SELECTED_PULSE = 0x44D3AA5A;
        public static final int NODE_LOCKED = 0xFF3C3932;
        public static final int NODE_UNLOCKABLE = 0xFF836126;
        public static final int NODE_TEXT = 0xFFF2E6C7;
        public static final int NODE_TEXT_MAXED = 0xFFD1FFF0;
        public static final int CONNECTOR_LOCKED = 0xFF65553B;
        public static final int CONNECTOR_STARTED = 0xFF7F6A43;
        public static final int CONNECTOR_UNLOCKED = 0xFFB58A44;
        public static final int CONNECTOR_MAXED = 0xFF76CFA9;
        public static final int CONNECTOR_STROKE = 2;
        public static final int CONNECTOR_LOCKED_STROKE = 1;

        public static final int NODE_STARTED_FILL = 0xFF6E6433;
        public static final int NODE_MAXED_FILL = 0xFF2C8D71;
        public static final int NODE_INNER_RING_LOCKED = 0x66493D29;
        public static final int NODE_INNER_RING_UNLOCKABLE = 0x998D6D2A;
        public static final int NODE_INNER_RING_STARTED = 0x99D7B96A;
        public static final int NODE_INNER_RING_MAXED = 0x99B5FFE4;
        public static final int NODE_ICON_LOCK = 0xFFC9AD73;
        public static final int NODE_ICON_PROGRESS = 0xFFFFDB8A;
        public static final int NODE_ICON_MAXED = 0xFFD3FFF0;
        public static final int NODE_ICON_Y_OFFSET = -4;
        public static final int NODE_PROGRESS_TRACK = 0x6631271A;
        public static final int NODE_PROGRESS_FILL = 0xFFE2C176;
        public static final int NODE_PROGRESS_BG = 0xCC211A11;
        public static final int NODE_PROGRESS_HEIGHT = 3;
        public static final int NODE_PROGRESS_SIDE_PADDING = 4;
        public static final int NODE_PROGRESS_BOTTOM_PADDING = 4;
        public static final int CONNECTOR_FLOW_MARK = 0x88F4DBAA;
        public static final int CONNECTOR_FLOW_MARK_SIZE = 2;

        public static final int PAN_MIN_X = -560;
        public static final int PAN_MAX_X = 260;
        public static final int PAN_MIN_Y = -320;
        public static final int PAN_MAX_Y = 180;
        public static final int SCROLL_PAN_STEP = 12;

        public static final int FOOTER_LEFT_X = 12;
        public static final int FOOTER_RIGHT_PAD = 8;
        public static final int FOOTER_TEXT_X = 12;
        public static final int FOOTER_TEXT_Y = 6;
        public static final int FOOTER_PRIMARY_COLOR = 0xFFF0DEB9;
        public static final int FOOTER_SECONDARY_COLOR = 0xFFC8B389;
        public static final int FOOTER_MIN_GAP = 14;

        public static final int TOOLTIP_WIDTH = 248;
        public static final int TOOLTIP_HEIGHT = 110;
        public static final int TOOLTIP_OFFSET_X = 12;
        public static final int TOOLTIP_OFFSET_Y = 10;
        public static final int TOOLTIP_BG = 0xF0282219;
        public static final int TOOLTIP_TEXT = 0xFFEDE0C6;
        public static final int STATUS_OK = 0xFF91E89C;
        public static final int STATUS_WARN = 0xFFFFA586;
        public static final int STATUS_LABEL = 0xFFD7C49B;
    }
}
