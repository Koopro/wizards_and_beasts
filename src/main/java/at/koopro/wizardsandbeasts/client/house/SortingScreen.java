package at.koopro.wizardsandbeasts.client.house;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.house.House;
import at.koopro.wizardsandbeasts.house.SortingChoiceC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The Sorting: the Hat's voice in the wearer's ear (<i>Philosopher's Stone</i> ch. 7). It says what it sees and where
 * it leans; the wearer lets it decide, or pleads against that house. The Hat's own patched brown, not a form.
 */
public class SortingScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 116;
    /** The black inside of the Hat over the wearer's eyes, its frayed brim below (tools/canon_gui.py). */
    private static final net.minecraft.resources.Identifier INSIDE = art("inside");
    /** The Hat itself, its face in the creases. */
    private static final net.minecraft.resources.Identifier HAT = art("hat");
    private static final int HAT_W = 56;
    private static final int HAT_H = 64;
    /** The voice: warm and pale against the dark leather. */
    private static final int VOICE = 0xFFE6D3A8;
    private static final int NAME = 0xFFD9B25A;

    private static net.minecraft.resources.Identifier art(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID,
                "textures/gui/artefact/sorting/" + name + ".png");
    }
    private static final GuiSkin SKIN = GuiSkin.SORTING_HAT;
    private static final int MIN_SKINNED = 2 * WizardsMetrics.PANEL_SPRITE_BORDER + 2;
    private static final String KEY = "gui.wizards_and_beasts.sorting.";

    private final House lean;
    private final boolean sure;
    private GuiScaleHelper.Layout layout;

    public SortingScreen(String house, boolean sure) {
        super(Component.translatable(KEY + "title"));
        House parsed = House.byId(house);
        this.lean = parsed != null ? parsed : House.GRYFFINDOR;
        this.sure = sure;
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        int px = layout.panelX();
        int py = layout.panelY();
        int pw = layout.panelW();
        int btnH = Math.max(MIN_SKINNED, layout.s(18));
        int btnW = layout.s(86);
        int btnY = py + layout.s(70);
        addRenderableWidget(ThemedButton.skinned(px + layout.s(72), btnY, btnW, btnH,
                Component.translatable(KEY + "decide"), () -> answer(false), SKIN));
        addRenderableWidget(ThemedButton.skinned(px + layout.s(164), btnY, btnW, btnH,
                Component.translatable(KEY + "plead", Component.translatable(lean.nameKey())), () -> answer(true), SKIN));
    }

    private void answer(boolean plead) {
        ClientPacketDistributor.sendToServer(new SortingChoiceC2SPayload(plead));
        onClose();
    }

    @Override
    public void render(@NonNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(gfx);
        int px = layout.panelX();
        int py = layout.panelY();
        int pw = layout.panelW();
        gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, INSIDE, px, py, 0.0F, 0.0F,
                pw, layout.panelH(), PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, HAT, px + layout.s(8), py + layout.s(10),
                0.0F, 0.0F, layout.s(HAT_W), layout.s(HAT_H), HAT_W, HAT_H, HAT_W, HAT_H);
        gfx.drawString(font, title, px + layout.s(72), py + layout.s(10), NAME, false);
        Component speech = Component.translatable(KEY + "sees." + lean.id())
                .append(" ")
                .append(Component.translatable(sure ? KEY + "sure" : KEY + "unsure"))
                .withStyle(ChatFormatting.ITALIC);
        List<FormattedCharSequence> lines = font.split(speech, pw - layout.s(82));
        int y = py + layout.s(24);
        for (FormattedCharSequence line : lines) {
            gfx.drawString(font, line, px + layout.s(72), y, VOICE, false);
            y += font.lineHeight + 1;
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
