package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.heritage.hud.ObscurialDarkPanelRenderer;
import at.koopro.wizardsandbeasts.client.heritage.hud.ObscurialHumanPanelRenderer;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.ui.HudVisibilityPolicy;
import at.koopro.wizardsandbeasts.client.ui.ObscurialUiModel;
import at.koopro.wizardsandbeasts.client.ui.UiStateProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class ObscurusOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "obscurus_hud");

    private ObscurusOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!HudVisibilityPolicy.shouldRenderObscurialHud(ClientHeritageDataState.get())) return;
        ObscurialUiModel obscurialUi = UiStateProjection.obscurialHud();
        if (obscurialUi.isDarkForm()) {
            ObscurialDarkPanelRenderer.render(graphics, mc, obscurialUi);
        } else if (obscurialUi.isHumanForm()) {
            ObscurialHumanPanelRenderer.render(graphics, mc, obscurialUi);
        }
    }
}
