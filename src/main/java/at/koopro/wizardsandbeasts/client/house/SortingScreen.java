package at.koopro.wizardsandbeasts.client.house;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
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
    private static final int PANEL_H = 112;
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
        int btnW = layout.s(112);
        int btnY = py + layout.panelH() - layout.s(12) - btnH;
        addRenderableWidget(ThemedButton.skinned(px + pw / 2 - btnW - layout.s(4), btnY, btnW, btnH,
                Component.translatable(KEY + "decide"), () -> answer(false), SKIN));
        addRenderableWidget(ThemedButton.skinned(px + pw / 2 + layout.s(4), btnY, btnW, btnH,
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
        McStylePanel.drawSkinPanel(gfx, SKIN, px, py, pw, layout.panelH());
        gfx.drawString(font, title, px + pw / 2 - font.width(title) / 2, py + layout.s(10), SKIN.ink(), false);
        McStylePanel.drawSkinDivider(gfx, SKIN, px + layout.s(12), py + layout.s(20), pw - layout.s(24));
        Component speech = Component.translatable(KEY + "sees." + lean.id())
                .append(" ")
                .append(Component.translatable(sure ? KEY + "sure" : KEY + "unsure"))
                .withStyle(ChatFormatting.ITALIC);
        List<FormattedCharSequence> lines = font.split(speech, pw - layout.s(28));
        int y = py + layout.s(30);
        for (FormattedCharSequence line : lines) {
            gfx.drawString(font, line, px + layout.s(14), y, SKIN.ink(), false);
            y += font.lineHeight + 1;
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
