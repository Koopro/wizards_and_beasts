package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.network.map.MapCloseC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The Marauder's Map before the words are said: a blank old parchment (<i>Prisoner of Azkaban</i> ch. 10).
 * "I solemnly swear that I am up to no good" makes the ink spread into the map; anything else and the paper
 * insults the reader, as it insulted Snape. Closing the map afterwards is "Mischief managed", and it is blank
 * again the next time it is unfolded.
 */
public class MapOathScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 110;
    private static final GuiSkin SKIN = MaraudersMapTextures.SKIN;
    private static final int MIN_SKINNED = 2 * WizardsMetrics.PANEL_SPRITE_BORDER + 2;
    private static final String KEY = "screen.wizards_and_beasts.marauders_map.";

    private EditBox field;
    private GuiScaleHelper.Layout layout;
    private Component reply = Component.empty();
    private boolean opened;

    public MapOathScreen() {
        super(Component.translatable(KEY + "blank"));
    }

    /** Whether the words a reader typed are the oath, forgiving case, punctuation and the last few words. */
    public static boolean isOath(String typed) {
        String plain = typed.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        return plain.startsWith("i solemnly swear");
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        field = new ThemedTextField(font, layout.panelX() + layout.s(12), layout.panelY() + layout.s(54),
                layout.panelW() - layout.s(24), Math.max(MIN_SKINNED, layout.s(18)),
                Component.translatable(KEY + "speak"))
                .inkHint(Component.translatable(KEY + "speak"))
                .skin(SKIN);
        field.setMaxLength(64);
        addRenderableWidget(field);
        setFocused(field);
        field.setFocused(true);
    }

    @Override
    public void render(@NonNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(gfx);
        int px = layout.panelX();
        int py = layout.panelY();
        int pw = layout.panelW();
        McStylePanel.drawSkinPanel(gfx, SKIN, px, py, pw, layout.panelH());
        drawCentred(gfx, Component.translatable(KEY + "blank_line").withStyle(ChatFormatting.ITALIC),
                px + pw / 2, py + layout.s(20), SKIN.muted());
        drawCentred(gfx, reply, px + pw / 2, py + layout.s(36), SKIN.ink());
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawCentred(GuiGraphics gfx, Component text, int cx, int y, int colour) {
        gfx.drawString(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && field != null) {
            speak(field.getValue());
            return true;
        }
        return super.keyPressed(event);
    }

    private void speak(String words) {
        if (words.isBlank()) {
            return;
        }
        if (isOath(words)) {
            opened = true;
            MaraudersMapScreen.inkSpreads();
            Minecraft.getInstance().setScreen(new MaraudersMapScreen());
            return;
        }
        // The map answers a stranger in the Marauders' own voices.
        int line = Math.floorMod(words.hashCode(), 4);
        reply = Component.translatable(KEY + "insult." + line);
        field.setValue("");
    }

    @Override
    public void removed() {
        if (!opened) {
            // Folded up unread: the server is streaming to an open map, so tell it the map is shut.
            ClientPacketDistributor.sendToServer(new MapCloseC2SPayload());
            ClientMapAtlas.close();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
