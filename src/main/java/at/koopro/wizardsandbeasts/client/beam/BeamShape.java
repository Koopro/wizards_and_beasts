package at.koopro.wizardsandbeasts.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * The <em>form</em> of a beam: straight ({@link Laser}) vs. zig-zag ({@link Lightning}) vs.
 * whatever anyone adds later. Registered by codec in {@link ModBeamShapes}; a new shape is a
 * record plus one registry line and never touches colour/bloom (that lives in {@link BeamStyle}).
 *
 * <p>{@code render} opens exactly one {@code SubmitNodeCollector.submitCustomGeometry} for the
 * whole beam — one render type per style — and drives {@link BeamGeometry#segment} inside the
 * callback. See BEAM_API_NOTES deviation #1 for why this replaces the prompt's
 * {@code MultiBufferSource} parameter.
 */
public interface BeamShape {

    /** The codec this instance serialises with; drives the registry dispatch codec. */
    MapCodec<? extends BeamShape> codec();

    /**
     * @param origin   beam start, relative to the rendering entity's position
     * @param target   beam end, relative to the rendering entity's position
     * @param progress 0..1 how far the beam has grown toward {@code target}
     * @param seed     deterministic per-caster seed (caster id) for jittered shapes
     * @param poseStack the stack passed to the renderer's {@code submit}
     */
    void render(BeamStyle style, Vec3 origin, Vec3 target, float progress, int seed,
                PoseStack poseStack, SubmitNodeCollector collector, int ticks, float partialTick);
}
