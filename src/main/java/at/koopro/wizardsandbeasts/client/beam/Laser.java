package at.koopro.wizardsandbeasts.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** A single straight segment from origin to target. The simplest shape. */
public record Laser() implements BeamShape {

    public static final MapCodec<Laser> CODEC = MapCodec.unit(Laser::new);

    @Override
    public MapCodec<? extends BeamShape> codec() {
        return CODEC;
    }

    @Override
    public void render(BeamStyle style, Vec3 origin, Vec3 target, float progress, int seed,
                       PoseStack poseStack, SubmitNodeCollector collector, int ticks, float partialTick) {
        collector.submitCustomGeometry(poseStack, BeamRenderTypes.forStyle(style), (pose, consumer) ->
                BeamGeometry.segment(style, origin, target, progress, pose.pose(), consumer, ticks, partialTick));
    }
}
