package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.network.spell.ImperioCommandC2SPayload;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Imperius command picker, on a parchment modal like every other one-question dialog in the mod.
 *
 * <p>2026-11-12: the sheet says what it is and whom it holds. Under the title the curse is marked as an
 * Unforgivable in rubric ink (the same red the Unforgivable Arts gate is written in), and a line names the
 * victim, so the caster is never ordering a blank. What a command will make them do is written at the foot
 * of the sheet for the button under the mouse, instead of only in a hover tooltip that covered the next
 * button. Keys 1-5 give the orders without the mouse.
 */
public class ImperioCommandScreen extends Screen {

    private static final String KEY = "gui." + WizardsAndBeastsMod.MODID + ".imperio.";
    private static final UiSurface S = UiSurface.PAGE;
    /** Clearance from the sheet's edge: its double rule runs 4-6px in. */
    private static final int EDGE = WizardsMetrics.SPACE_L;
    private static final int PANEL_W = 210;
    private static final int BUTTON_H = 20;
    private static final int GAP = WizardsMetrics.SPACE_S;
    /** Lines kept at the foot for the hovered command's description. */
    private static final int FOOT_LINES = 2;

    private final List<ThemedButton> commandButtons = new ArrayList<>();
    private final Component victimLine;
    private int panelX;
    private int panelY;
    private int panelH;
    private int footY;

    public ImperioCommandScreen() {
        super(Component.translatable("spell." + WizardsAndBeastsMod.MODID + ".imperio.name"));
        this.victimLine = victimLine();
    }

    /** "<name> is yours to command", or a nameless line when the victim is not loaded on this client. */
    private static Component victimLine() {
        Minecraft mc = Minecraft.getInstance();
        @Nullable UUID victim = ClientSignatureSpellState.getImperioBoundVictim();
        if (victim != null && mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (victim.equals(entity.getUUID())) {
                    return Component.translatable(KEY + "bound", entity.getDisplayName());
                }
            }
        }
        return Component.translatable(KEY + "bound_unknown");
    }

    private int headH() {
        // Title + rule, then the rubric and the victim line.
        return font.lineHeight + 1 + WizardsMetrics.DIVIDER_H + WizardsMetrics.SPACE_XS
                + 2 * WizardsMetrics.LINE_TIGHT + WizardsMetrics.SPACE_S;
    }

    private int footH() {
        return WizardsMetrics.SPACE_S + 1 + WizardsMetrics.SPACE_XS + FOOT_LINES * WizardsMetrics.LINE_BODY;
    }

    @Override
    protected void init() {
        commandButtons.clear();
        ImperioCommand[] commands = ImperioCommand.values();
        panelH = EDGE + headH() + commands.length * (BUTTON_H + GAP) + GAP + BUTTON_H + footH() + EDGE;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        int bx = panelX + EDGE;
        int bw = PANEL_W - 2 * EDGE;
        int y = panelY + EDGE + headH();
        for (ImperioCommand cmd : commands) {
            String id = cmd.name().toLowerCase(Locale.ROOT);
            commandButtons.add(addRenderableWidget(new ThemedButton(bx, y, bw, BUTTON_H,
                    Component.translatable(KEY + "command." + id), () -> send(cmd))));
            y += BUTTON_H + GAP;
        }
        addRenderableWidget(new ThemedButton(bx, y + GAP, bw, BUTTON_H,
                Component.translatable("gui.cancel"), this::onClose));
        footY = y + GAP + BUTTON_H + WizardsMetrics.SPACE_S;
    }

    private void send(ImperioCommand cmd) {
        ClientPacketDistributor.sendToServer(new ImperioCommandC2SPayload(cmd.ordinal()));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: Screen.renderWithTooltipAndSubtitles already ran it for
        // this frame, and a second call blurs twice ("Can only blur once per frame").
        int x = panelX + EDGE;
        int w = PANEL_W - 2 * EDGE;
        Ui.panel(graphics, S, panelX, panelY, PANEL_W, panelH);
        int y = Ui.title(graphics, font, S, title, x, panelY + EDGE, w, null);
        UiText.drawFit(graphics, font, S, Component.translatable(KEY + "rubric"), x, y, w, S.bad());
        UiText.drawFit(graphics, font, S, victimLine, x, y + WizardsMetrics.LINE_TIGHT, w, S.ink2());

        super.render(graphics, mouseX, mouseY, partialTick);

        Ui.hairline(graphics, S, x, footY, w);
        Component foot = Component.translatable(KEY + "hint");
        ImperioCommand[] commands = ImperioCommand.values();
        for (int i = 0; i < commandButtons.size(); i++) {
            ThemedButton button = commandButtons.get(i);
            if (button.isHovered() || button.isFocused()) {
                foot = Component.translatable(KEY + "command." + commands[i].name().toLowerCase(Locale.ROOT) + ".desc");
                break;
            }
        }
        UiText.drawWrapped(graphics, font, S, UiText.Role.LABEL, foot, x, footY + 1 + WizardsMetrics.SPACE_XS, w,
                FOOT_LINES);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int index = event.key() - GLFW.GLFW_KEY_1;
        ImperioCommand[] commands = ImperioCommand.values();
        if (index >= 0 && index < commands.length) {
            send(commands[index]);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
