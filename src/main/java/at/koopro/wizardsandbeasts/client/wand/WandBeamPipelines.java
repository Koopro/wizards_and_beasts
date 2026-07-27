package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Render pipelines for the wand beam.
 *
 * <p>The beam used to draw through {@code NeoForgeRenderTypes.getUnlitTranslucent}, i.e. ordinary
 * alpha blending. Alpha blending cannot bloom: stacking three translucent layers just averages them
 * toward the background, so the beam read as a flat pastel ribbon. Real energy beams are additive —
 * overlapping layers sum toward white, which is what produces the hot filament inside a coloured
 * halo. This pipeline is {@link RenderPipelines#EYES} (emissive, unlit, no depth write) with
 * {@link BlendFunction#ADDITIVE} and culling off so the strips are visible from both faces.
 */
public final class WandBeamPipelines {

    public static final RenderPipeline BEAM_ADDITIVE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pipeline/wand_beam_additive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(false)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .build();

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(BEAM_ADDITIVE);
    }

    private WandBeamPipelines() {}
}
