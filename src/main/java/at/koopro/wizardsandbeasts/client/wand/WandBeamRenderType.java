package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/** Additive, emissive render types for the wand beam: the scrolling arc strip and the endpoint glow. */
public final class WandBeamRenderType {

    public static final Identifier BEAM_TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/effect/wand_beam.png");
    public static final Identifier GLOW_TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/effect/wand_beam_glow.png");

    /**
     * The strip scrolls U past 1.0 every frame, so it must wrap horizontally; V stays clamped so the
     * soft edge of the band is never sampled from the opposite side. Linear filtering keeps the arc
     * smooth instead of showing texel stair-steps when the beam passes close to the camera.
     */
    private static final Function<Identifier, RenderType> SCROLLING = Util.memoize(
            texture -> RenderType.create("wandb_beam_strip",
                    RenderSetup.builder(WandBeamPipelines.BEAM_ADDITIVE)
                            .withTexture("Sampler0", texture,
                                    () -> RenderSystem.getSamplerCache().getSampler(
                                            AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                                            FilterMode.LINEAR, FilterMode.LINEAR, false))
                            .createRenderSetup()));

    private static final Function<Identifier, RenderType> CLAMPED = Util.memoize(
            texture -> RenderType.create("wandb_beam_glow",
                    RenderSetup.builder(WandBeamPipelines.BEAM_ADDITIVE)
                            .withTexture("Sampler0", texture,
                                    () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                            .createRenderSetup()));

    public static RenderType beamStrip() {
        return SCROLLING.apply(BEAM_TEXTURE);
    }

    public static RenderType beamGlow() {
        return CLAMPED.apply(GLOW_TEXTURE);
    }

    private WandBeamRenderType() {}
}
