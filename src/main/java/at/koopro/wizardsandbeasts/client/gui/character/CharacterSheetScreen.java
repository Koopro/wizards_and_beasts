package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
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
    private static final int BG_W = 320;
    private static final int BG_H = 240;

    // Column split
    private static final int LEFT_W  = 120;
    private static final int TITLE_H = 14;
    private static final int TAB_H   = 14;
    /**
     * Clearance from the sheet's outer edge. {@code gui_chrome.dossier_backdrop} paints the
     * frame — an inked border, two brass rules and the corner brackets — out to 6px, so
     * anything drawn inside 7px of the edge sits on top of it.
     */
    private static final int FRAME_PAD = 7;
    /** Clearance from the internal column divider, which is a single rule rather than a frame. */
    private static final int DIVIDER_PAD = 4;
    /** Side inset of the model viewport, which narrows it toward the figure's own aspect. */
    private static final int VIEWPORT_SIDE_INSET = 20;

    // Palette
    private static final int COLOR_BG       = 0xFF2A1E0F;
    private static final int COLOR_BG_HI    = 0xFF44321A;
    private static final int COLOR_BG_SH    = 0xFF1A0F00;
    private static final int COLOR_DIVIDER  = 0xFF44321A;
    private static final int COLOR_TITLE    = 0xFFFFEECC;
    private static final int COLOR_TITLE_SUB = 0xFFAA9977;
    private static final int COLOR_TAB_ACT  = 0xFF4A3A1A;
    private static final int COLOR_TAB_INACT = 0xFF251A0A;
    private static final int COLOR_TAB_HI  = 0xFF66501E;
    private static final int COLOR_TAB_SH  = 0xFF150D00;
    private static final int COLOR_TAB_TXT = 0xFFCCBB99;
    private static final int COLOR_EFFECT_BG = 0xFF1E1408;
    private static final int COLOR_EFFECT_TXT = 0xFFCCBB99;
    private static final int COLOR_EFFECT_MORE = 0xFF887766;

    private static final int MAX_EFFECTS_SHOWN = 6;
    private static final int EFFECT_CHIP_H     = 8;

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
        // Layout.panel, not the downscale-only computeScale this used to call: that capped
        // the sheet at 1.0 forever, so on anything above a small window the 320x240 panel sat
        // marooned in the middle of the screen. panel() grows it to fill the space (to 1.35x)
        // and still shrinks it to fit small windows.
        GuiScaleHelper.Layout layout = GuiScaleHelper.Layout.panel(width, height, BG_W, BG_H);
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

        // Main panel — themed leather/parchment background art
        McStylePanel.drawTexture(g, CharacterSheetTextures.BACKGROUND, bgX, bgY, BG_W, BG_H, BG_W, BG_H);

        // Title bar
        renderTitleBar(g);

        // Vertical divider between columns
        g.fill(bgX + LEFT_W, bgY + TITLE_H, bgX + LEFT_W + 1, bgY + BG_H, COLOR_DIVIDER);

        // Left column (viewport look-at uses design-space mouse)
        renderLeftColumn(g, partialTick, (float) toDesignX(mouseX), (float) toDesignY(mouseY));

        // Right column (hover tests run in design space)
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

    private void renderLeftColumn(@NonNull GuiGraphics g, float partialTick, float mouseX, float mouseY) {
        int colX = bgX;
        int colY = bgY + TITLE_H;
        int colW = LEFT_W;
        int colH = BG_H - TITLE_H;

        LocalPlayer player = minecraft.player;
        if (!(player instanceof LocalPlayer lp)) return;

        // 3D viewport, portrait aspect and centred in the column.
        //
        // It used to be colW-12 by 112 — near square, 108x112 — around a figure that is
        // 0.6 blocks wide by 1.8 tall. Even filling the height, a player only covers about
        // a third of that width, so most of the box read as empty black. A portrait box
        // wastes far less of itself on a standing figure.
        int vpW = colW - VIEWPORT_SIDE_INSET * 2;
        int vpH = 124;
        int vpX = colX + VIEWPORT_SIDE_INSET;
        int vpY = colY + 4;
        viewport.render(g, vpX, vpY, vpW, vpH, partialTick, lp, mouseX, mouseY);

        // Heritage block
        int hbY = colY + vpH + 6;
        HeritageBlockWidget.draw(g, colX + 4, hbY, colW - 8);

        // Vitals (health + xp)
        int vitY = hbY + HeritageBlockWidget.height() + 4;
        int barW = colW - 8;
        VitalsBarWidget.drawHealth(g, colX + 4, vitY, barW,
                lp.getHealth(), lp.getMaxHealth());
        VitalsBarWidget.drawXp(g, colX + 4, vitY + 16, barW,
                lp.experienceLevel, lp.experienceProgress);

        // Active effects
        int efY = vitY + 32 + 4;
        renderActiveEffects(g, colX + 4, efY, colW - 8);
    }

    private void renderActiveEffects(@NonNull GuiGraphics g, int x, int y, int w) {
        if (minecraft.player == null) return;
        Collection<MobEffectInstance> effects = minecraft.player.getActiveEffects();
        if (effects.isEmpty()) return;

        Font font = minecraft.font;
        List<MobEffectInstance> list = new ArrayList<>(effects);
        int shown = Math.min(list.size(), MAX_EFFECTS_SHOWN);

        for (int i = 0; i < shown; i++) {
            MobEffectInstance fx = list.get(i);
            String name = Component.translatable(fx.getEffect().value().getDescriptionId()).getString();
            McStylePanel.drawNineSlice(g, CharacterSheetTextures.PANEL, x, y + i * (EFFECT_CHIP_H + 1),
                    w, EFFECT_CHIP_H, CharacterSheetTextures.PANEL_SIZE, CharacterSheetTextures.PANEL_BORDER);
            // Shrunk to fit instead of chopped mid-word, which is what plainSubstrByWidth did
            // to every effect name longer than the chip ("Fire Resistan").
            GuiText.drawFitted(g, font, name, x + 2, y + i * (EFFECT_CHIP_H + 1) + 1,
                    w - 4, COLOR_EFFECT_TXT);
        }
        if (list.size() > MAX_EFFECTS_SHOWN) {
            int remainder = list.size() - MAX_EFFECTS_SHOWN;
            g.drawString(font, "+" + remainder + " more",
                    x, y + shown * (EFFECT_CHIP_H + 1),
                    COLOR_EFFECT_MORE, false);
        }
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
        int colX = bgX + LEFT_W + 1;
        int colY = bgY + TITLE_H;
        int colW = BG_W - LEFT_W - 1;
        return new int[] {
            colX + DIVIDER_PAD,
            colY + TAB_H + DIVIDER_PAD,
            colW - DIVIDER_PAD - FRAME_PAD,
            BG_H - TITLE_H - TAB_H - DIVIDER_PAD - FRAME_PAD,
        };
    }

    private void renderRightColumn(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int colX = bgX + LEFT_W + 1;
        int colY = bgY + TITLE_H;
        int colW = BG_W - LEFT_W - 1;

        // Tab buttons row
        renderTabButtons(g, colX, colY, colW, mouseX, mouseY);

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
            int colX = bgX + LEFT_W + 1;
            int colY = bgY + TITLE_H;
            int colW = BG_W - LEFT_W - 1;
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

        // Viewport zoom
        if (viewport.mouseScrolled(dmx, dmy, scrollY)) return true;

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
