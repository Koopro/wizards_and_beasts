package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.character.tab.AttributesTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.CharacterTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.SkillsTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.SpellsTab;
import at.koopro.wizardsandbeasts.client.gui.character.widget.HeritageBlockWidget;
import at.koopro.wizardsandbeasts.client.gui.character.widget.PlayerModelViewport;
import at.koopro.wizardsandbeasts.client.gui.character.widget.VitalsBarWidget;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read-only Character Sheet screen.
 *
 * <p>Layout (320 × 240 background):
 * <pre>
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │ Title bar (14px)                                              │
 *  ├──────────────────────┬───────────────────────────────────────┤
 *  │ Left column (120px)  │ Right column (200px)                  │
 *  │  - 3D viewport       │  - [Attributes] [Skills] [Spells]     │
 *  │  - Heritage block    │  - Active tab content                 │
 *  │  - Vitals (hp + xp)  │                                       │
 *  │  - Active effects    │                                       │
 *  └──────────────────────┴───────────────────────────────────────┘
 * </pre>
 */
public final class CharacterSheetScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Background dimensions
    private static final int BG_W = 400;
    private static final int BG_H = 220;

    // Column split
    private static final int TITLE_H = 14;
    private static final int TAB_H   = 14;

    /**
     * Three columns: the figure, the things that are true of the character whichever tab is open,
     * and the tab content.
     *
     * <p>The two-column sheet put identity — heritage, lineage, wand, vocation, effects — in the
     * same 120px rail as the model viewport, and gave the tabs everything else. That made the left
     * rail a dumping ground and left identity competing with tab content for the reader's attention
     * even though it never changes when a tab does.
     */
    private static final int FIGURE_W   = 96;
    private static final int IDENTITY_W = 92;
    private static final int COL_GAP    = 6;
    // Spacing comes from WizardsMetrics rather than this file. The three values here were 7, 4
    // and 20 -- two of them off any grid, and the 7 in particular was reverse-engineered from
    // `gui_chrome.dossier_backdrop` painting its frame out to 6px. SPACE_M still clears that.

    /** Clearance from the sheet's outer edge, which the backdrop's own frame occupies to 6px. */
    private static final int FRAME_PAD = WizardsMetrics.SPACE_M;
    /** Clearance from the internal column divider, which is a single rule rather than a frame. */
    private static final int DIVIDER_PAD = WizardsMetrics.SPACE_S;
    /**
     * Usable width of the left column: clear of the sheet frame on the outside, clear of the
     * column divider on the inside.
     *
     * <p>The left column was drawing at {@code colX + 4} with width {@code colW - 8}, which put it
     * inside the backdrop's own frame — that art carries rules at 0, 2 and 3 plus an inner rule and
     * corner brackets at 6. Only the right column had been migrated, so the sheet cleared its frame
     * on one side and sat on it on the other.
     */
    /** Left edge of each column, in design space. */
    private int figureX() {
        return bgX + FRAME_PAD;
    }

    private int identityX() {
        return figureX() + FIGURE_W + COL_GAP;
    }

    private int contentX() {
        return identityX() + IDENTITY_W + COL_GAP;
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

    /** Side inset of the model viewport, which narrows it toward the figure's own aspect. */
    private static final int VIEWPORT_SIDE_INSET = WizardsMetrics.SPACE_XL;

    // Palette
    private static final int COLOR_DIVIDER  = 0xFF44321A;
    private static final int COLOR_TITLE    = 0xFFFFEECC;
    private static final int COLOR_TITLE_SUB = 0xFFAA9977;
    private static final int COLOR_TAB_TXT = 0xFFCCBB99;
    private static final int COLOR_EFFECT_TXT = 0xFFCCBB99;
    private static final int COLOR_EFFECT_MORE = 0xFF887766;
    /** Kept off pure red/green: this column is warm parchment ink, not a status LED. */
    private static final int COLOR_EFFECT_GOOD = 0xFF8FBF6A;
    private static final int COLOR_EFFECT_BAD  = 0xFFCC7755;

    private static final int MAX_EFFECTS_SHOWN = 6;
    /** One text line per effect, matching the heritage rows above. */
    private static final int EFFECT_ROW_H      = WizardsMetrics.LINE_BODY;

    // Tabs
    private enum Tab {
        ATTRIBUTES("gui.wizards_and_beasts.character_sheet.tab.attributes"),
        SKILLS("gui.wizards_and_beasts.character_sheet.tab.skills"),
        SPELLS("gui.wizards_and_beasts.character_sheet.tab.spells");

        final String key;
        Tab(String key) { this.key = key; }
    }

    private Tab activeTab = Tab.ATTRIBUTES;

    /**
     * Screen to hand back to when the inventory key is pressed, or null when the sheet was
     * opened some other way (the {@code C} keybind, a server packet). Only set when the
     * sheet was reached from the inventory, so {@code E} returns you exactly where you were
     * instead of dumping you into the world.
     */
    private final @Nullable Screen returnTo;

    // Tab instances (content renderers)
    private final CharacterTab attributesTab = new AttributesTab();
    private final CharacterTab skillsTab     = new SkillsTab();
    private final CharacterTab spellsTab     = new SpellsTab();

    // Viewport widget
    private final PlayerModelViewport viewport = new PlayerModelViewport();

    // Cached screen origin (clamped) and shrink factor for small screens
    private int bgX, bgY;
    private float guiScale = 1.0f;

    // One-time PREVIEW debug log flag
    private boolean previewLogged;

    public CharacterSheetScreen() {
        this(null);
    }

    /** @param returnTo screen the inventory key hands back to, or null to just close. */
    public CharacterSheetScreen(@Nullable Screen returnTo) {
        super(Component.translatable("gui.wizards_and_beasts.character_sheet.title"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        // Layout.fit, not Layout.panel. panel() magnifies to 1.35x on a large window, and since
        // everything here is text that magnified the font too — the sheet's labels came out a third
        // larger than the inventory's, which is the screen a player compares it against. fit()
        // never upscales, so the text is exactly the size vanilla draws at, and still shrinks the
        // sheet to fit a small window.
        GuiScaleHelper.Layout layout = GuiScaleHelper.Layout.fit(width, height, BG_W, BG_H);
        guiScale = layout.scale();
        bgX = layout.panelX();
        bgY = layout.panelY();

        if (ModuleManager.isPreview(Module.CHARACTER_SHEET) && !previewLogged) {
            LOGGER.debug("[W&B] CharacterSheetScreen opened in PREVIEW mode.");
            previewLogged = true;
        }
    }

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background is drawn by the framework's own renderBackground() call (overridden
        // below); calling it again here would draw twice.

        // All panel drawing anchors at (bgX, bgY), so a transform that keeps that
        // point fixed while scaling lets the existing design-space code run unchanged.
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(bgX * (1.0f - guiScale), bgY * (1.0f - guiScale));
        pose.scale(guiScale, guiScale);

        // The sheet's background is the shared nine-sliced theme panel rather than the fixed
        // 320x240 `character_sheet/background.png` it used to blit. That art could only ever be
        // one size, so every change to the sheet's footprint meant regenerating it; the theme
        // panel is cut from a 32px sprite and is exact at any size.
        McStylePanel.drawThemedPanel(g, bgX, bgY, BG_W, BG_H);

        // Title bar
        renderTitleBar(g);

        // Column rules
        g.fill(identityX() - COL_GAP / 2, columnsTop(), identityX() - COL_GAP / 2 + 1,
                columnsBottom(), COLOR_DIVIDER);
        g.fill(contentX() - COL_GAP / 2, columnsTop(), contentX() - COL_GAP / 2 + 1,
                columnsBottom(), COLOR_DIVIDER);

        // Figure column (viewport look-at uses design-space mouse)
        renderFigureColumn(g, partialTick, (float) toDesignX(mouseX), (float) toDesignY(mouseY));

        // Identity column — unchanging facts, so they never compete with the tabs
        renderIdentityColumn(g);

        // Content column (hover tests run in design space)
        renderRightColumn(g, (int) toDesignX(mouseX), (int) toDesignY(mouseY), partialTick);

        pose.popMatrix();

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Map a screen-space coordinate into the unscaled design space anchored at (bgX, bgY). */
    private double toDesignX(double screenX) {
        return bgX + (screenX - bgX) / guiScale;
    }

    private double toDesignY(double screenY) {
        return bgY + (screenY - bgY) / guiScale;
    }

    // ── Title bar ──────────────────────────────────────────────────────────

    private void renderTitleBar(@NonNull GuiGraphics g) {
        Font font = minecraft.font;

        g.fill(bgX, bgY, bgX + BG_W, bgY + TITLE_H, 0xFF1A1005);

        g.drawString(font, "Character Sheet", bgX + 4, bgY + 3, COLOR_TITLE, false);

        String playerName = minecraft.player != null
                ? minecraft.player.getName().getString() : "";
        int nameW = font.width(playerName);
        g.drawString(font, playerName, bgX + BG_W - 4 - nameW, bgY + 3, COLOR_TITLE_SUB, false);
    }

    // ── Left column ────────────────────────────────────────────────────────

    /**
     * The figure and the two readouts that describe its condition right now.
     *
     * <p>Vitals live here rather than with the identity facts on purpose: health and XP are things
     * about the body standing in the viewport, and they change while you watch. Heritage does not.
     */
    private void renderFigureColumn(@NonNull GuiGraphics g, float partialTick,
                                    float mouseX, float mouseY) {
        LocalPlayer player = minecraft.player;
        if (!(player instanceof LocalPlayer lp)) return;

        int colX = figureX();
        int top = columnsTop();

        // Portrait aspect: a player is 0.6 blocks wide by 1.8 tall, so a near-square box spends
        // most of itself on empty black either side of the figure.
        int vpH = columnsBottom() - top - (WizardsMetrics.LINE_SECTION * 2) - DIVIDER_PAD * 2;
        viewport.render(g, colX, top, FIGURE_W, vpH, partialTick, lp, mouseX, mouseY);

        int vitY = top + vpH + DIVIDER_PAD;
        McStylePanel.drawDivider(g, colX, vitY, FIGURE_W);
        vitY += WizardsMetrics.DIVIDER_H;
        VitalsBarWidget.drawHealth(g, colX, vitY, FIGURE_W, lp.getHealth(), lp.getMaxHealth());
        VitalsBarWidget.drawXp(g, colX, vitY + WizardsMetrics.LINE_SECTION, FIGURE_W,
                lp.experienceLevel, lp.experienceProgress);
    }

    /** Heritage and the active effects: true of the character regardless of which tab is open. */
    private void renderIdentityColumn(@NonNull GuiGraphics g) {
        int colX = identityX();
        int y = columnsTop();

        HeritageBlockWidget.draw(g, colX, y, IDENTITY_W);
        y += HeritageBlockWidget.height() + DIVIDER_PAD;

        McStylePanel.drawDivider(g, colX, y, IDENTITY_W);
        y += WizardsMetrics.DIVIDER_H;

        renderActiveEffects(g, colX, y, IDENTITY_W);
    }

    private void renderActiveEffects(@NonNull GuiGraphics g, int x, int y, int w) {
        if (minecraft.player == null) return;
        Font font = minecraft.font;

        g.drawString(font, "Effects", x, y, COLOR_EFFECT_TXT, false);
        y += WizardsMetrics.LINE_BODY;

        Collection<MobEffectInstance> effects = minecraft.player.getActiveEffects();
        if (effects.isEmpty()) {
            // Said explicitly rather than left blank: an empty region under a divider reads as a
            // panel that failed to draw, not as a character who happens to have no effects.
            g.drawString(font, "None", x, y, COLOR_EFFECT_MORE, false);
            return;
        }

        List<MobEffectInstance> list = new ArrayList<>(effects);
        int shown = Math.min(list.size(), MAX_EFFECTS_SHOWN);

        for (int i = 0; i < shown; i++) {
            drawEffectRow(g, font, x, y + i * EFFECT_ROW_H, w, list.get(i));
        }
        if (list.size() > MAX_EFFECTS_SHOWN) {
            int remainder = list.size() - MAX_EFFECTS_SHOWN;
            g.drawString(font, "+" + remainder + " more",
                    x, y + shown * EFFECT_ROW_H,
                    COLOR_EFFECT_MORE, false);
        }
    }

    /**
     * One effect: its name, and how long is left.
     *
     * <p>These were nine-slice chips, which could not work at this size — {@code PANEL} carries a
     * 3px border, so an 8px-tall chip is 6px of border around 2px of middle, and the label was drawn
     * on top of the frame rather than inside it. The result read as an empty text field stretched
     * across the column whatever the effect was called. Rows now match the heritage block directly
     * above them, which is the same shape of information: flat text, no box.
     *
     * <p>Colour carries the one thing a chip never did — whether the effect is doing you a favour.
     */
    private void drawEffectRow(@NonNull GuiGraphics g, @NonNull Font font,
                               int x, int y, int w, @NonNull MobEffectInstance fx) {
        MobEffect effect = fx.getEffect().value();

        String name = Component.translatable(effect.getDescriptionId()).getString();
        if (fx.getAmplifier() > 0) {
            name = name + " " + roman(fx.getAmplifier() + 1);
        }

        String duration = formatDuration(fx);
        int durW = font.width(duration);

        // Shrunk to fit rather than chopped mid-word, which is what plainSubstrByWidth did to
        // every effect name longer than the column ("Fire Resistan").
        GuiText.drawFitted(g, font, name, x, y, w - durW - 3, effectColor(effect));
        g.drawString(font, duration, x + w - durW, y, COLOR_EFFECT_MORE, false);
    }

    /** Beneficial reads warm, harmful reads hot, neutral stays the column's own ink. */
    private static int effectColor(@NonNull MobEffect effect) {
        return switch (effect.getCategory()) {
            case BENEFICIAL -> COLOR_EFFECT_GOOD;
            case HARMFUL    -> COLOR_EFFECT_BAD;
            case NEUTRAL    -> COLOR_EFFECT_TXT;
        };
    }

    /** {@code m:ss}, or {@code ∞} for the effects that do not run out. */
    @NonNull
    private static String formatDuration(@NonNull MobEffectInstance fx) {
        if (fx.isInfiniteDuration()) return "∞";
        int seconds = fx.getDuration() / 20;
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * Level numeral. Vanilla's {@code potion.potency.N} keys only ship for 1–5, and mod effects
     * are not bound by that, so the numeral is built rather than looked up.
     */
    @NonNull
    private static String roman(int value) {
        if (value < 1 || value > 3999) return Integer.toString(value);
        int[] nums = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] sym = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < nums.length; i++) {
            while (value >= nums[i]) {
                value -= nums[i];
                out.append(sym[i]);
            }
        }
        return out.toString();
    }

    // ── Right column ───────────────────────────────────────────────────────

    /**
     * The active tab's content rect, as {@code {x, y, w, h}}.
     *
     * <p>Rendering and the scroll hit-test each computed this themselves and had already
     * drifted apart — the hit-test used {@code bgX + LEFT_W + 3} against a render origin of
     * {@code bgX + LEFT_W + 5} — so the top two pixels of the content scrolled the sheet
     * rather than the tab. One definition now serves both.
     */
    private int[] contentRect() {
        int top = columnsTop() + TAB_H + DIVIDER_PAD;
        return new int[] {
            contentX(),
            top,
            contentW(),
            columnsBottom() - top,
        };
    }

    private void renderRightColumn(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Tab buttons row
        renderTabButtons(g, contentX(), columnsTop(), contentW(), mouseX, mouseY);

        // Content area below tabs.
        //
        // The 2px inset this used to carry was narrower than the backdrop's own frame:
        // `gui_chrome.dossier_backdrop` draws its rules and corner brackets out to 6px from
        // the sheet edge, so attribute cards and their text landed on top of the border and
        // the right-hand column read as clipped. The outer edges now clear the frame; the
        // inner edge only has to clear the column divider, so it stays tighter.
        int[] r = contentRect();
        activeTabRenderer().render(g, r[0], r[1], r[2], r[3], partialTick);
    }

    private void renderTabButtons(@NonNull GuiGraphics g, int x, int y, int w,
                                  int mouseX, int mouseY) {
        Font font = minecraft.font;
        Tab[] tabs = Tab.values();
        int tabW = w / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            Tab tab  = tabs[i];
            int tx   = x + i * tabW;
            int tw   = (i == tabs.length - 1) ? w - i * tabW : tabW;
            boolean active  = tab == activeTab;
            boolean hovered = mouseX >= tx && mouseX < tx + tw
                           && mouseY >= y && mouseY < y + TAB_H;

            McStylePanel.drawNineSlice(g, (active || hovered) ? CharacterSheetTextures.PANEL_SEL : CharacterSheetTextures.PANEL,
                    tx, y, tw, TAB_H, CharacterSheetTextures.PANEL_SIZE, CharacterSheetTextures.PANEL_BORDER);

            // Shrink rather than spill: three tabs share the right column, so a longer
            // translation of "Attributes" would otherwise run out over its neighbours.
            String label = Component.translatable(tab.key).getString();
            GuiText.drawFittedCentered(g, font, label, tx + 2, y + 3, tw - 4, COLOR_TAB_TXT);
        }
    }

    @NonNull
    private CharacterTab activeTabRenderer() {
        return switch (activeTab) {
            case ATTRIBUTES -> attributesTab;
            case SKILLS     -> skillsTab;
            case SPELLS     -> spellsTab;
        };
    }

    // ── Input handling ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = toDesignX(event.x());
        double mouseY = toDesignY(event.y());
        int button = event.button();

        // Check tab buttons
        if (button == 0) {
            int colX = contentX();
            int colY = columnsTop();
            int colW = contentW();
            Tab[] tabs = Tab.values();
            int tabW = colW / tabs.length;

            for (int i = 0; i < tabs.length; i++) {
                int tx = colX + i * tabW;
                int tw = (i == tabs.length - 1) ? colW - i * tabW : tabW;
                if (mouseX >= tx && mouseX < tx + tw
                        && mouseY >= colY && mouseY < colY + TAB_H) {
                    activeTab = tabs[i];
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    /**
     * Closes on the inventory key as well as Escape, mirroring
     * {@code AbstractContainerScreen#keyPressed}. Reached from the inventory, {@code E} hands
     * you back to it rather than closing out to the world — the sheet is a tab off the
     * inventory, so the key that opened it should also step back out of it.
     */
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (super.keyPressed(event)) return true;
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
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        double dmx = toDesignX(mouseX);
        double dmy = toDesignY(mouseY);

        // The wheel scrolls tab content and nothing else. It used to zoom the player preview,
        // which let the figure be resized out of the framing the columns are composed around —
        // and put a zoom control on a read-only sheet where nothing else is adjustable.

        // Tab content scroll
        int[] r = contentRect();
        int contentX = r[0], contentY = r[1], contentW = r[2], contentH = r[3];
        if (dmx >= contentX && dmx < contentX + contentW
                && dmy >= contentY && dmy < contentY + contentH) {
            return activeTabRenderer().mouseScrolled(dmx, dmy, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }




    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Deliberately no blur pass: the parchment sheet reads better over a crisp world.
        // Dim overlay only.
        renderMenuBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
