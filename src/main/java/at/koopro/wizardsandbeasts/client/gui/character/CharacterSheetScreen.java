package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.tab.AttributesTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.BeastsTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.CareerTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.CharacterTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.EducationTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.IdentityTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.RecordTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.SkillsTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.SpellsTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.TravelTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.WandTab;
import at.koopro.wizardsandbeasts.client.gui.character.widget.PlayerModelViewport;
import at.koopro.wizardsandbeasts.client.gui.character.widget.VitalsBarWidget;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Character Sheet: the one place a player reads who their wizard is and what they are becoming.
 *
 * <p>Layout (408 × 234 design space):
 * <pre>
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │ Title                                              Player    │
 *  ├────────────┬────────────┬────────────────────────────────────┤
 *  │ figure     │ ▣ Identity │ the open page                      │
 *  │ (viewport) │ ▣ Attribs  │                                    │
 *  │            │ ▣ Magic    │                                    │
 *  │            │ ▣ Wand  …  │                                    │
 *  │ health, xp │ ▣ Travel   │                                    │
 *  └────────────┴────────────┴────────────────────────────────────┘
 * </pre>
 *
 * <p>The middle column was the identity column — heritage, lineage, traits, effects — beside two rows of tabs
 * above the content. With nine tabs the rows clipped every label to two letters ("At…", "Sk…") and the identity
 * facts competed with the content for a column they did not change in. Since 2026-10-15 the middle column is a
 * page rail with an icon and a full name per page, the identity facts live on the Identity page (the one the sheet
 * opens on), and the content gets the column's full height.
 *
 * <p>Read-only by design except where a page sends an intent (contract buttons, companion orders, travel actions):
 * every number on every page is synced or server-built state, and the sheet never edits its own copy.
 */
public final class CharacterSheetScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Background dimensions. Grown from 400x220 when the sheet became parchment: the frame's double rule runs
    // 4-6px in, so the padding went from 8 to 12 and the title moved inside the frame.
    private static final int BG_W = 408;
    private static final int BG_H = 234;

    /** Frame, title line and the rule under it. */
    private static final int TITLE_H = 24;
    /** Baseline row of the title text, clear of the frame's rules at 4 and 6. */
    private static final int TITLE_TEXT_Y = 10;
    /** The divider under the title: its rules land at +3 and +5, so at 21 and 23. */
    private static final int TITLE_RULE_Y = 18;

    /** One rail entry: a 16px icon with a texel above and below it. Ten entries and their gaps fit 194px. */
    private static final int RAIL_ROW_H = 18;
    private static final int RAIL_ROW_GAP = 1;
    private static final int RAIL_ICON_X = 2;
    private static final int RAIL_LABEL_X = RAIL_ICON_X + SheetIcons.SIZE + 3;
    /** Corner mark on a page fed by a PREVIEW module. Amber: a caution, not an error. */
    private static final int COLOR_PREVIEW_DOT = WizardsPalette.GILT;

    private static final int FIGURE_W = 96;
    private static final int RAIL_W = 92;
    private static final int COL_GAP = 6;

    /** Clearance from the sheet's outer edge: the parchment frame's inner rule sits at 6px. */
    private static final int FRAME_PAD = WizardsMetrics.SPACE_L;
    /** Clearance from the internal column divider, which is a single rule rather than a frame. */
    private static final int DIVIDER_PAD = WizardsMetrics.SPACE_S;

    private static final int COLOR_DIVIDER = WizardsPalette.PAGE_DEEP;
    private static final int COLOR_TITLE = WizardsPalette.PAGE_INK;
    private static final int COLOR_TITLE_SUB = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_TAB_TXT = WizardsPalette.PAGE_INK;
    private static final int COLOR_TAB_TXT_IDLE = WizardsPalette.PAGE_INK_2;

    private SheetTab activeTab = SheetTab.IDENTITY;
    /** The hovered rail entry's card this frame, or null. */
    private @Nullable List<Component> railTooltip;
    /** Whether the opening page has been told it is showing; {@code init} runs again on every resize. */
    private boolean opened;

    /**
     * Screen to hand back to when the inventory key is pressed, or null when the sheet was opened some other way
     * (the {@code C} keybind, a server packet). Only set when the sheet was reached from the inventory, so {@code E}
     * returns you exactly where you were instead of dumping you into the world.
     */
    private final @Nullable Screen returnTo;

    private final Map<SheetTab, CharacterTab> pages = new EnumMap<>(SheetTab.class);

    private final PlayerModelViewport viewport = new PlayerModelViewport();

    // Cached screen origin (clamped) and shrink factor for small screens
    private int bgX, bgY;
    private float guiScale = 1.0f;

    private boolean previewLogged;

    public CharacterSheetScreen() {
        this(null);
    }

    /** @param returnTo screen the inventory key hands back to, or null to just close. */
    public CharacterSheetScreen(@Nullable Screen returnTo) {
        super(Component.translatable("gui.wizards_and_beasts.character_sheet.title"));
        this.returnTo = returnTo;
        pages.put(SheetTab.IDENTITY, new IdentityTab(this::select));
        pages.put(SheetTab.ATTRIBUTES, new AttributesTab());
        pages.put(SheetTab.MAGIC, new SpellsTab());
        pages.put(SheetTab.WAND, new WandTab());
        pages.put(SheetTab.SKILLS, new SkillsTab());
        pages.put(SheetTab.CREATURES, new BeastsTab());
        pages.put(SheetTab.EDUCATION, new EducationTab());
        pages.put(SheetTab.PROFESSION, new CareerTab());
        pages.put(SheetTab.REPUTATION, new RecordTab());
        pages.put(SheetTab.TRAVEL, new TravelTab());
    }

    /** The sheet opened on a given page. */
    public static CharacterSheetScreen onTab(SheetTab tab) {
        CharacterSheetScreen sheet = new CharacterSheetScreen();
        sheet.activeTab = tab;
        return sheet;
    }

    /** The sheet opened on its Creatures page. */
    public static CharacterSheetScreen onBeastsTab() {
        return onTab(SheetTab.CREATURES);
    }

    /** The sheet opened on its Education page: subjects, grades and the next qualification. */
    public static CharacterSheetScreen onEducationTab() {
        return onTab(SheetTab.EDUCATION);
    }

    /** The sheet opened on its Profession page: where a promotion or a new contract is read. */
    public static CharacterSheetScreen onCareerTab() {
        return onTab(SheetTab.PROFESSION);
    }

    @Override
    protected void init() {
        // Layout.fit, not Layout.panel. panel() magnifies to 1.35x on a large window, and since everything here is
        // text that magnified the font too — the sheet's labels came out a third larger than the inventory's, which
        // is the screen a player compares it against. fit() never upscales, so the text is exactly the size vanilla
        // draws at, and still shrinks the sheet to fit a small window.
        GuiScaleHelper.Layout layout = GuiScaleHelper.Layout.fit(width, height, BG_W, BG_H);
        guiScale = layout.scale();
        bgX = layout.panelX();
        bgY = layout.panelY();

        // Probe the bespoke tab art once per open rather than every frame. A pack that overrides this namespace
        // without shipping panel.png would otherwise paint the rail in the missing-texture checkerboard.
        CharacterSheetTextures.refresh();

        if (!opened) {
            opened = true;
            page().onShown();
        }

        if (ModuleManager.isPreview(Module.CHARACTER_SHEET) && !previewLogged) {
            LOGGER.debug("[W&B] CharacterSheetScreen opened in PREVIEW mode.");
            previewLogged = true;
        }
    }

    /** Opens a page; the Identity page's cards call this. */
    private void select(SheetTab tab) {
        if (activeTab != tab) {
            activeTab = tab;
            page().onShown();
        }
    }

    // ── layout ─────────────────────────────────────────────────────────────

    private int figureX() {
        return bgX + FRAME_PAD;
    }

    private int railX() {
        return figureX() + FIGURE_W + COL_GAP;
    }

    private int contentX() {
        return railX() + RAIL_W + COL_GAP;
    }

    private int contentW() {
        return bgX + BG_W - FRAME_PAD - contentX();
    }

    /** Top of the three columns, below the title bar. */
    private int columnsTop() {
        return bgY + TITLE_H + DIVIDER_PAD;
    }

    private int columnsBottom() {
        return bgY + BG_H - FRAME_PAD;
    }

    /** The open page's rect, as {@code {x, y, w, h}}: one definition for drawing, clicking and scrolling. */
    private int[] contentRect() {
        return new int[] {contentX(), columnsTop(), contentW(), columnsBottom() - columnsTop()};
    }

    /** Rail entry {@code i}'s top edge. */
    private int railRowY(int i) {
        return columnsTop() + i * (RAIL_ROW_H + RAIL_ROW_GAP);
    }

    @NonNull
    private CharacterTab page() {
        return pages.get(activeTab);
    }

    // ── drawing ────────────────────────────────────────────────────────────

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background is drawn by the framework's own renderBackground() call (overridden below); calling it
        // again here would draw twice.

        // All panel drawing anchors at (bgX, bgY), so a transform that keeps that point fixed while scaling lets
        // the design-space code run unchanged.
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(bgX * (1.0f - guiScale), bgY * (1.0f - guiScale));
        pose.scale(guiScale, guiScale);

        McStylePanel.drawThemedPanel(g, bgX, bgY, BG_W, BG_H);
        renderTitleBar(g);

        g.fill(railX() - COL_GAP / 2, columnsTop(), railX() - COL_GAP / 2 + 1, columnsBottom(), COLOR_DIVIDER);
        g.fill(contentX() - COL_GAP / 2, columnsTop(), contentX() - COL_GAP / 2 + 1, columnsBottom(), COLOR_DIVIDER);

        int designX = (int) toDesignX(mouseX);
        int designY = (int) toDesignY(mouseY);
        renderFigureColumn(g, partialTick, (float) toDesignX(mouseX), (float) toDesignY(mouseY));
        renderRail(g, designX, designY);
        int[] r = contentRect();
        page().render(g, r[0], r[1], r[2], r[3], designX, designY, partialTick);

        pose.popMatrix();

        // Outside the scale transform on purpose. The framework draws a deferred tooltip after the whole screen, in
        // screen space; a card positioned inside the pose above would land correctly only at scale 1.0.
        List<Component> tooltip = page().consumeTooltip();
        if (tooltip == null || tooltip.isEmpty()) {
            tooltip = railTooltip;
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            g.setTooltipForNextFrame(minecraft.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Map a screen-space coordinate into the unscaled design space anchored at (bgX, bgY). */
    private double toDesignX(double screenX) {
        return bgX + (screenX - bgX) / guiScale;
    }

    private double toDesignY(double screenY) {
        return bgY + (screenY - bgY) / guiScale;
    }

    private void renderTitleBar(@NonNull GuiGraphics g) {
        Font font = minecraft.font;
        // Written on the sheet rather than on a bar painted over it: a dark title strip across the top would cover
        // the frame's own rules.
        g.drawString(font, this.title.getString(), bgX + FRAME_PAD, bgY + TITLE_TEXT_Y, COLOR_TITLE, false);
        String playerName = minecraft.player != null ? minecraft.player.getName().getString() : "";
        int nameW = font.width(playerName);
        g.drawString(font, playerName, bgX + BG_W - FRAME_PAD - nameW, bgY + TITLE_TEXT_Y, COLOR_TITLE_SUB, false);
        McStylePanel.drawDivider(g, bgX + FRAME_PAD, bgY + TITLE_RULE_Y, BG_W - 2 * FRAME_PAD);
    }

    /**
     * The figure and the two readouts that describe its condition right now. Vitals live here rather than on a
     * page: health and XP are things about the body standing in the viewport, and they change while you watch.
     */
    private void renderFigureColumn(@NonNull GuiGraphics g, float partialTick, float mouseX, float mouseY) {
        if (!(minecraft.player instanceof LocalPlayer lp)) return;
        int colX = figureX();
        int top = columnsTop();

        // Portrait aspect: a player is 0.6 blocks wide by 1.8 tall, so a near-square box spends most of itself on
        // empty black either side of the figure.
        int vpH = columnsBottom() - top - (WizardsMetrics.LINE_SECTION * 2) - DIVIDER_PAD * 2;
        viewport.render(g, colX, top, FIGURE_W, vpH, partialTick, lp, mouseX, mouseY);

        int vitY = top + vpH + DIVIDER_PAD;
        McStylePanel.drawDivider(g, colX, vitY, FIGURE_W);
        vitY += WizardsMetrics.DIVIDER_H;
        VitalsBarWidget.drawHealth(g, colX, vitY, FIGURE_W, lp.getHealth(), lp.getMaxHealth());
        VitalsBarWidget.drawXp(g, colX, vitY + WizardsMetrics.LINE_SECTION, FIGURE_W,
                lp.experienceLevel, lp.experienceProgress);
    }

    /**
     * The page rail: one entry per page, icon and full name, the open one raised. Hovering an entry says what the
     * page holds, so a player can find "where do I see my wand's bond" without opening every page.
     */
    private void renderRail(@NonNull GuiGraphics g, int mouseX, int mouseY) {
        Font font = minecraft.font;
        railTooltip = null;
        SheetTab[] tabs = SheetTab.values();
        int x = railX();
        for (int i = 0; i < tabs.length; i++) {
            SheetTab tab = tabs[i];
            int y = railRowY(i);
            boolean active = tab == activeTab;
            boolean hovered = mouseX >= x && mouseX < x + RAIL_W && mouseY >= y && mouseY < y + RAIL_ROW_H;

            CharacterSheetTextures.drawPanel(g, x, y, RAIL_W, RAIL_ROW_H, active, hovered);
            SheetIcons.draw(g, tab, x + RAIL_ICON_X, y + (RAIL_ROW_H - SheetIcons.SIZE) / 2);
            // Ellipsised, never shrunk: the rail is sized for the English names; a longer translation shortens
            // and the hover card carries the full name.
            GuiText.drawFitted(g, font, tab.label().getString(), x + RAIL_LABEL_X, y + (RAIL_ROW_H - 8) / 2,
                    RAIL_W - RAIL_LABEL_X - 4, active ? COLOR_TAB_TXT : COLOR_TAB_TXT_IDLE);

            // A page whose data comes from a PREVIEW module is marked, so a player reading half-finished numbers
            // knows they are half-finished rather than wrong.
            if (tab.previewSourced()) {
                g.fill(x + RAIL_W - 4, y + 2, x + RAIL_W - 2, y + 4, COLOR_PREVIEW_DOT);
            }
            if (hovered) {
                railTooltip = List.of(tab.label(), tab.description());
            }
        }
    }

    // ── input ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = toDesignX(event.x());
        double mouseY = toDesignY(event.y());
        if (event.button() == 0) {
            SheetTab[] tabs = SheetTab.values();
            int x = railX();
            for (int i = 0; i < tabs.length; i++) {
                int y = railRowY(i);
                if (mouseX >= x && mouseX < x + RAIL_W && mouseY >= y && mouseY < y + RAIL_ROW_H) {
                    select(tabs[i]);
                    return true;
                }
            }
        }
        int[] r = contentRect();
        if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]
                && page().mouseClicked(mouseX, mouseY, event.button())) {
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    /**
     * Up and down step through the pages, so the sheet can be read without the mouse. Closes on the inventory key
     * as well as Escape, mirroring {@code AbstractContainerScreen#keyPressed}: reached from the inventory, {@code E}
     * hands you back to it rather than closing out to the world.
     */
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP) {
            SheetTab[] tabs = SheetTab.values();
            int step = event.key() == GLFW.GLFW_KEY_DOWN ? 1 : tabs.length - 1;
            select(tabs[(activeTab.ordinal() + step) % tabs.length]);
            return true;
        }
        if (minecraft != null
                && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(event))) {
            if (returnTo != null) {
                minecraft.setScreen(returnTo);
            } else {
                onClose();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double dmx = toDesignX(mouseX);
        double dmy = toDesignY(mouseY);
        // The wheel scrolls page content and nothing else. It used to zoom the player preview, which let the figure
        // be resized out of the framing the columns are composed around.
        int[] r = contentRect();
        if (dmx >= r[0] && dmx < r[0] + r[2] && dmy >= r[1] && dmy < r[1] + r[3]) {
            return page().mouseScrolled(dmx, dmy, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Deliberately no blur pass: the parchment sheet reads better over a crisp world. Dim overlay only. (In
        // 1.21.11 the framework calls this before render(), and blurring twice a frame throws — see lessons.md.)
        renderMenuBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
