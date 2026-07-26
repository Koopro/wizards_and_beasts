package at.koopro.wizardsandbeasts.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A jagged bolt: {@code segments} straight pieces whose interior joints are jittered off the
 * straight line by up to {@code spread} pixels. The last joint lands exactly on {@code target}.
 *
 * @param segments  how many pieces (1..32)
 * @param spread    lateral jitter of interior joints, in pixels
 * @param frequency the bolt re-rolls its shape every {@code frequency} ticks
 */
public record Lightning(int segments, float spread, int frequency) implements BeamShape {

    /** One pixel in blocks — {@link #spread} is authored in pixels, jitter is applied in blocks. */
    private static final float PX = 1f / 16f;

    public static final MapCodec<Lightning> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, 32).optionalFieldOf("segments", 5).forGetter(Lightning::segments),
            Codec.FLOAT.optionalFieldOf("spread", 5f).forGetter(Lightning::spread),
            Codec.intRange(1, 40).optionalFieldOf("frequency", 2).forGetter(Lightning::frequency)
    ).apply(instance, Lightning::new));

    @Override
    public MapCodec<? extends BeamShape> codec() {
        return CODEC;
    }

    @Override
    public void render(BeamStyle style, Vec3 origin, Vec3 target, float progress, int seed,
                       PoseStack poseStack, SubmitNodeCollector collector, int ticks, float partialTick) {
        collector.submitCustomGeometry(poseStack, BeamRenderTypes.forStyle(style), (pose, consumer) -> {
            Matrix4f base = pose.pose();

            // Deterministic seed with integer division: re-rolling every frame would be unusable noise
            // at 120fps. The division freezes the shape for `frequency` ticks — it holds still, then
            // snaps to a new shape. Because the seed is only caster-id + tick, every client draws the
            // same bolt with zero network traffic.
            int freq = Math.max(1, frequency);
            RandomSource random = RandomSource.create(seed + (long) (ticks / freq));

            Vec3 step = target.subtract(origin).scale(1.0 / segments);
            Vec3 cursor = origin;

            for (int i = 0; i < segments; i++) {
                float segStart = (float) i / segments;
                float segEnd = (float) (i + 1) / segments;
                // Spread `progress` across the segments so the bolt grows piece-by-piece rather than
                // scaling uniformly.
                float segProgress = progress <= segStart ? 0f
                        : progress >= segEnd ? 1f
                        : (progress - segStart) / (segEnd - segStart);
                if (segProgress <= 0f) {
                    break;
                }

                // Last segment lands exactly on target so the optics don't wobble beside the point
                // where damage actually resolves.
                Vec3 end = (i == segments - 1)
                        ? target
                        : origin.add(step.scale(i + 1)).add(jitter(random, spread * PX));

                BeamGeometry.segment(style, cursor, end, segProgress, base, consumer, ticks, partialTick);
                cursor = end;
            }
        });
    }

    /** One shared {@code RandomSource} across all segments — the stream runs through, each joint differs. */
    private static Vec3 jitter(RandomSource random, float amp) {
        return new Vec3(
                (random.nextFloat() - 0.5f) * 2f * amp,
                (random.nextFloat() - 0.5f) * 2f * amp,
                (random.nextFloat() - 0.5f) * 2f * amp);
    }
}
