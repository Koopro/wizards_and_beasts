package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * The two render types a beam can draw with. Both wrap <em>stock, already-registered</em>
 * pipelines, so there is no custom {@code RenderPipeline} to register and no shader file to
 * ship — and both stock pipelines are {@code POSITION_COLOR} with no lightmap sampler, which
 * is exactly what a fullbright beam wants (a lightmap fed a constant fullbright value is a
 * no-op, so the extra vertex data would be wasted).
 *
 * <p>Both use {@link LayeringTransform#VIEW_OFFSET_Z_LAYERING} to nudge the beam toward the
 * camera in depth, so a beam that ends flat against a wall does not z-fight the wall face.
 */
public final class BeamRenderTypes {

    /**
     * Additive blend (the {@code LIGHTNING} pipeline uses {@code BlendFunction.LIGHTNING}). The
     * default case: bright magic that adds light onto the scene.
     */
    public static final RenderType BEAM_ADDITIVE = RenderType.create(
            "wab_beam_additive",
            RenderSetup.builder(RenderPipelines.LIGHTNING)
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

    private BeamRenderTypes() {}
}
