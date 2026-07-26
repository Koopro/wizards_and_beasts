package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.network.spell.ImperioCommandC2SPayload;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * Minimal Imperius command picker (predefined commands only).
 */
public class ImperioCommandScreen extends Screen {

    public ImperioCommandScreen() {
        super(Component.literal("Imperio"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y0 = height / 4;
        int gap = 24;
        int i = 0;
        for (ImperioCommand cmd : ImperioCommand.values()) {
            int y = y0 + i * gap;
            addRenderableWidget(Button.builder(Component.literal(cmd.name()), b -> {
                        ClientPacketDistributor.sendToServer(new ImperioCommandC2SPayload(cmd.ordinal()));
                        Minecraft.getInstance().setScreen(null);
                    })
                    .bounds(cx - 90, y, 180, 20)
                    .build());
            i++;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> Minecraft.getInstance().setScreen(null))
                .bounds(cx - 60, y0 + i * gap + 8, 120, 20)
                .build());
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: Screen.renderWithTooltipAndSubtitles already ran it for
        // this frame, and a second call blurs twice ("Can only blur once per frame").
        graphics.drawCenteredString(font, title, width / 2, height / 6, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
