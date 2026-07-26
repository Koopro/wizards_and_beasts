package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The two render types a beam can draw with. Both reuse <em>stock</em> shaders, so there is no
 * shader file to ship — and both are {@code POSITION_COLOR} with no lightmap sampler, which is
 * exactly what a fullbright beam wants (a lightmap fed a constant fullbright value is a no-op,
 * so the extra vertex data would be wasted).
 *
 * <p>Both use {@link LayeringTransform#VIEW_OFFSET_Z_LAYERING} to nudge the beam toward the
 * camera in depth, so a beam that ends flat against a wall does not z-fight the wall face.
 */
public final class BeamRenderTypes {

    /**
     * Stock {@code LIGHTNING} minus depth writes.
     *
     * <p>{@code RenderPipelines.LIGHTNING} leaves {@code writeDepth} at its default of {@code true}.
     * That is fine for a vanilla bolt — one opaque-ish tube — but {@link BeamGeometry} stacks the
     * core box and every bloom shell into a <em>single</em> draw with additive blending. With depth
     * writes on, each shell writes the depth of its own surface and the next, wider shell then fails
     * the {@code LEQUAL} test against it, so the shells punch holes in one another instead of
     * summing. Depth <em>testing</em> stays on: the beam must still be occluded by world geometry.
     *
     * <p>Vanilla does the same thing one pipeline further down — {@code DRAGON_RAYS} is the identical
     * lightning shader with {@code withDepthWrite(false)}, for exactly this class of volumetric ray.
     *
     * <p>Culling is off for the same reason. {@code LIGHTNING} leaves {@code cull} at its default of
     * {@code true}, which is right for a vanilla bolt — a hard, surface-like shape. A beam is meant
     * to read as a glowing <em>volume</em>, and under additive blending every face adds light, so
     * drawing the far wall of the tube as well as the near one is what makes the light look like it
     * passes through the whole rod rather than sitting on a flat ribbon.
     *
     * <p>Only the additive type does this. {@link #BEAM_TRANSLUCENT} stays culled: alpha blending is
     * order-dependent, so compositing the far faces under the near ones would just double-darken.
     */
    public static final RenderPipeline BEAM_ADDITIVE_PIPELINE = RenderPipelines.LIGHTNING.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pipeline/beam_additive"))
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /**
     * Additive blend (inherited from {@code LIGHTNING}: {@code BlendFunction.LIGHTNING} is
     * {@code SRC_ALPHA, ONE}). The default case: bright magic that adds light onto the scene.
     */
    public static final RenderType BEAM_ADDITIVE = RenderType.create(
            "wab_beam_additive",
            RenderSetup.builder(BEAM_ADDITIVE_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    /**
     * Normal alpha blend ({@code DEBUG_FILLED_BOX} pipeline: {@code BlendFunction.TRANSLUCENT},
     * depth-write off). Needed for dark magic — additive blending can never draw darker than the
     * background, so a black/purple beam would be invisible against a bright sky. Sorted on
     * upload so overlapping translucent faces composite in a stable order.
     */
    public static final RenderType BEAM_TRANSLUCENT = RenderType.create(
            "wab_beam_translucent",
            RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .sortOnUpload()
                    .createRenderSetup());

    /** The render type a style renders with: additive by default, translucent for dark magic. */
    public static RenderType forStyle(BeamStyle style) {
        return style.additive() ? BEAM_ADDITIVE : BEAM_TRANSLUCENT;
    }

    /**
     * Makes {@link #BEAM_ADDITIVE_PIPELINE} known to the game so it gets compiled. Only the derived
     * pipeline needs this; {@code DEBUG_FILLED_BOX} behind {@link #BEAM_TRANSLUCENT} is stock and
     * already registered (and already {@code writeDepth = false} via {@code DEBUG_FILLED_SNIPPET}).
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(BEAM_ADDITIVE_PIPELINE);
    }

    private BeamRenderTypes() {}
}
