package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.character.tab.AttributesTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.CharacterTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.SkillsTab;
import at.koopro.wizardsandbeasts.client.gui.character.tab.SpellsTab;
import at.koopro.wizardsandbeasts.client.gui.character.widget.HeritageBlockWidget;
import at.koopro.wizardsandbeasts.client.gui.character.widget.PlayerModelViewport;
import at.koopro.wizardsandbeasts.client.gui.character.widget.VitalsBarWidget;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.NonNull;
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
        super(Component.translatable("gui.wizards_and_beasts.character_sheet.title"));
    }

    @Override
    protected void init() {
        guiScale = GuiScaleHelper.computeScale(BG_W, BG_H, width, height, GuiScaleHelper.DEFAULT_MARGIN);
        bgX = GuiScaleHelper.clampedLeft(Math.round(BG_W * guiScale), width, GuiScaleHelper.DEFAULT_MARGIN);
        bgY = GuiScaleHelper.clampedTop(Math.round(BG_H * guiScale), height, GuiScaleHelper.DEFAULT_MARGIN);

        if (ModuleManager.isPreview(Module.CHARACTER_SHEET) && !previewLogged) {
            LOGGER.debug("[W&B] CharacterSheetScreen opened in PREVIEW mode.");
            previewLogged = true;
        }
    }

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background
        renderBackground(g, mouseX, mouseY, partialTick);

        // All panel drawing anchors at (bgX, bgY), so a transform that keeps that
        // point fixed while scaling lets the existing design-space code run unchanged.
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(bgX * (1.0f - guiScale), bgY * (1.0f - guiScale));
        pose.scale(guiScale, guiScale);

        // Main panel
        McStylePanel.drawPanel(g, bgX, bgY, BG_W, BG_H, COLOR_BG, COLOR_BG_HI, COLOR_BG_SH);

        // Title bar
        renderTitleBar(g);

        // Vertical divider between columns
        g.fill(bgX + LEFT_W, bgY + TITLE_H, bgX + LEFT_W + 1, bgY + BG_H, COLOR_DIVIDER);

        // Left column
        renderLeftColumn(g, partialTick);

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

    private void renderLeftColumn(@NonNull GuiGraphics g, float partialTick) {
        int colX = bgX;
        int colY = bgY + TITLE_H;
        int colW = LEFT_W;
        int colH = BG_H - TITLE_H;

        LocalPlayer player = minecraft.player;
        if (!(player instanceof LocalPlayer lp)) return;

        // 3D viewport: x=colX+6, y=colY+4, w=colW-12, h=112
        int vpX = colX + 6;
        int vpY = colY + 4;
        int vpW = colW - 12;
        int vpH = 112;
        viewport.render(g, vpX, vpY, vpW, vpH, partialTick, lp);

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
            String name = font.plainSubstrByWidth(
                    Component.translatable(fx.getEffect().value().getDescriptionId()).getString(),
                    w - 2);
            McStylePanel.drawPanel(g, x, y + i * (EFFECT_CHIP_H + 1),
                    w, EFFECT_CHIP_H, COLOR_EFFECT_BG, COLOR_DIVIDER, COLOR_BG_SH);
            g.drawString(font, name, x + 2, y + i * (EFFECT_CHIP_H + 1) + 1,
                    COLOR_EFFECT_TXT, false);
        }
        if (list.size() > MAX_EFFECTS_SHOWN) {
            int remainder = list.size() - MAX_EFFECTS_SHOWN;
            g.drawString(font, "+" + remainder + " more",
                    x, y + shown * (EFFECT_CHIP_H + 1),
                    COLOR_EFFECT_MORE, false);
        }
    }

    // ── Right column ───────────────────────────────────────────────────────

    private void renderRightColumn(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int colX = bgX + LEFT_W + 1;
        int colY = bgY + TITLE_H;
        int colW = BG_W - LEFT_W - 1;

        // Tab buttons row
        renderTabButtons(g, colX, colY, colW, mouseX, mouseY);

        // Content area below tabs
        int contentX = colX + 2;
        int contentY = colY + TAB_H + 2;
        int contentW = colW - 4;
        int contentH = BG_H - TITLE_H - TAB_H - 4;

        activeTabRenderer().render(g, contentX, contentY, contentW, contentH, partialTick);
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

            int bgColor = active ? COLOR_TAB_ACT : (hovered ? COLOR_TAB_INACT : COLOR_TAB_INACT);
            int hiColor = (active || hovered) ? COLOR_TAB_HI : COLOR_TAB_INACT;
            McStylePanel.drawPanel(g, tx, y, tw, TAB_H, bgColor, hiColor, COLOR_TAB_SH);

            String label = Component.translatable(tab.key).getString();
            int lw = font.width(label);
            g.drawString(font, label, tx + (tw - lw) / 2, y + 3, COLOR_TAB_TXT, false);
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

        // Viewport drag start
        if (viewport.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (viewport.mouseReleased(toDesignX(event.x()), toDesignY(event.y()), event.button())) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (viewport.mouseDragged(toDesignX(event.x()), toDesignY(event.y()), event.button(), deltaX, deltaY)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        double dmx = toDesignX(mouseX);
        double dmy = toDesignY(mouseY);

        // Viewport zoom
        if (viewport.mouseScrolled(dmx, dmy, scrollY)) return true;

        // Tab content scroll
        int contentX = bgX + LEFT_W + 3;
        int contentY = bgY + TITLE_H + TAB_H + 2;
        int contentW = BG_W - LEFT_W - 5;
        int contentH = BG_H - TITLE_H - TAB_H - 4;
        if (dmx >= contentX && dmx < contentX + contentW
                && dmy >= contentY && dmy < contentY + contentH) {
            return activeTabRenderer().mouseScrolled(dmx, dmy, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }




    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Skip processBlurEffect — limited to once per frame. Draw only the dim overlay.
        renderMenuBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
