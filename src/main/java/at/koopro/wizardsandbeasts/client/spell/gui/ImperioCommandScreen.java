package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.network.spell.ImperioCommandC2SPayload;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * The Imperius command picker: one button per predefined command, on a parchment modal like every other
 * one-question dialog in the mod. Each command is named in words and says in its tooltip what the
 * victim will do; the enum constant ({@code FOLLOW_CASTER}) used to be the label.
 */
public class ImperioCommandScreen extends Screen {

    private static final String KEY = "gui." + WizardsAndBeastsMod.MODID + ".imperio.";
    private static final UiSurface S = UiSurface.PAGE;
    /** Clearance from the sheet's edge: its double rule runs 4-6px in. */
    private static final int EDGE = WizardsMetrics.SPACE_L;
    private static final int PANEL_W = 200;
    private static final int BUTTON_H = 20;
    private static final int GAP = WizardsMetrics.SPACE_S;

    private int panelX;
    private int panelY;
    private int panelH;

    public ImperioCommandScreen() {
        super(Component.translatable("spell." + WizardsAndBeastsMod.MODID + ".imperio.name"));
    }

    private int titleBlockH() {
        return font.lineHeight + 1 + WizardsMetrics.DIVIDER_H + WizardsMetrics.SPACE_XS;
    }

    @Override
    protected void init() {
        ImperioCommand[] commands = ImperioCommand.values();
        panelH = EDGE + titleBlockH() + commands.length * (BUTTON_H + GAP) + GAP + BUTTON_H + EDGE;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        int bx = panelX + EDGE;
        int bw = PANEL_W - 2 * EDGE;
        int y = panelY + EDGE + titleBlockH();
        for (ImperioCommand cmd : commands) {
            String id = cmd.name().toLowerCase(Locale.ROOT);
            ThemedButton button = new ThemedButton(bx, y, bw, BUTTON_H,
                    Component.translatable(KEY + "command." + id), () -> send(cmd));
            button.setTooltip(Tooltip.create(Component.translatable(KEY + "command." + id + ".desc")));
            addRenderableWidget(button);
            y += BUTTON_H + GAP;
        }
        addRenderableWidget(new ThemedButton(bx, y + GAP, bw, BUTTON_H,
                Component.translatable("gui.cancel"), this::onClose));
    }

    private void send(ImperioCommand cmd) {
        ClientPacketDistributor.sendToServer(new ImperioCommandC2SPayload(cmd.ordinal()));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: Screen.renderWithTooltipAndSubtitles already ran it for
        // this frame, and a second call blurs twice ("Can only blur once per frame").
        Ui.panel(graphics, S, panelX, panelY, PANEL_W, panelH);
        Ui.title(graphics, font, S, title, panelX + EDGE, panelY + EDGE, PANEL_W - 2 * EDGE, null);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
