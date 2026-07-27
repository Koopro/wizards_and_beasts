package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a {@link BeamEntity} by re-tracing the beam every frame.
 *
 * <p>The trace is re-run here (not read from the server's 20Hz result) on purpose: at 20Hz the
 * hit point would step in visible chunks as the mouse moves. The hit point is deliberately
 * <strong>not</strong> networked — both sides compute it from the same block data, saving one
 * {@code Vec3}/tick per active beam per player.
 */
public class BeamEntityRenderer extends EntityRenderer<BeamEntity, BeamRenderState> {

    public BeamEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public BeamRenderState createRenderState() {
        return new BeamRenderState();
    }

    /**
     * The beam stretches far beyond its tiny hitbox, so frustum-culling it against that hitbox
     * would drop the whole beam the moment the caster left the frame.
     */
    @Override
    protected boolean affectedByCulling(BeamEntity beam) {
        return false;
    }

    @Override
    public void extractRenderState(BeamEntity beam, BeamRenderState state, float partialTick) {
        super.extractRenderState(beam, state, partialTick);
        // The editor wins while it is open, so a slider drag shows on the next frame instead of
        // needing a recast. Read here rather than written into the entity: the entity keeps the
        // spell's real look, so closing the editor restores it without re-resolving anything.
        boolean edit = BeamStyleEditor.active;
        state.style = edit ? BeamStyleEditor.style() : beam.getStyle();
        state.shape = edit ? BeamStyleEditor.shape() : beam.getShape();
        state.seed = beam.getCasterId();
        state.ticks = beam.tickCount;
        state.partialTick = partialTick;
        state.progress = beam.getProgress(partialTick);

        Entity caster = beam.level().getEntity(beam.getCasterId());
        if (caster instanceof Player player) {
            // Same resolver the server aims with, ramped the same way, so the drawn end and the
            // point where damage resolves cannot drift apart. The start is still the wand tip —
            // the ray is eye-anchored, the optics are not.
            Vec3 origin = WandTipTracker.resolve((LivingEntity) caster, partialTick);
            BeamRay ray = BeamRayResolver.resolve(
                    player, partialTick, beam.currentReach(partialTick), BeamRayResolver.LIVING_FILTER);
            // Relative to the entity's interpolated position — that is where the submit pose sits.
            Vec3 reference = beam.getPosition(partialTick);
            state.origin = origin.subtract(reference);
            state.target = ray.end().subtract(reference);
            state.valid = true;
        } else {
            state.valid = false;
        }
    }

    @Override
    public void submit(BeamRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!state.valid || state.progress <= 0f || state.style == null || state.shape == null) {
            return;
        }
        state.shape.render(state.style, state.origin, state.target, state.progress, state.seed,
                poseStack, collector, state.ticks, state.partialTick);
    }
}
