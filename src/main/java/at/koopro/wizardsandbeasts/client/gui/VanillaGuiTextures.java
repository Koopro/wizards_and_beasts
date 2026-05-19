package at.koopro.wizardsandbeasts.client.gui;

import net.minecraft.resources.Identifier;

/**
 * Vanilla GUI texture identifiers used as placeholders until mod-specific art exists.
 */
public final class VanillaGuiTextures {

    /**
     * Demo-mode style panel background (256×256). Stretched to any width/height via {@link McStylePanel#drawTexturedPanel}.
     */
    public static final Identifier DEMO_BACKGROUND_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/demo_background.png");

    private VanillaGuiTextures() {}
}
