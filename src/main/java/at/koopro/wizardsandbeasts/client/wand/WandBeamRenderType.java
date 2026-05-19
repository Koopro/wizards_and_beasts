package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;

/** Textured additive-style beam strip for held-channel wands. */
public final class WandBeamRenderType {

    public static final Identifier BEAM_TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/effect/wand_beam.png");

    public static RenderType beamStrip() {
        return NeoForgeRenderTypes.getUnlitTranslucent(BEAM_TEXTURE);
    }

    private WandBeamRenderType() {}
}
