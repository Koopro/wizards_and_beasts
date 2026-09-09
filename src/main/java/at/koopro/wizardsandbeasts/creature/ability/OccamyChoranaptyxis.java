package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Occamy): choranaptyxis — it grows or shrinks to fill the space it is in.
 *
 * <p>This drives {@code Attributes.SCALE} through {@link GenericBeastEntity#applySizeScale}, so one
 * number moves <b>both</b> the hitbox and the model: vanilla recomputes dimensions from
 * {@code getScale()} and GeckoLib multiplies the rendered model by the same value. An earlier
 * version of this ability wrote a render-only synced float and said so in its own javadoc —
 * "the hitbox never changes" — which made a grown Occamy a big picture around a small box, and left
 * the one thing choranaptyxis is for (fitting the space) unimplemented.
 *
 * <h2>Want, then need</h2>
 * <ul>
 *   <li><b>Want</b> — it swells toward {@code maxScale} while it holds a target, and settles back to
 *       {@code calmScale} when nothing is worth being big for.</li>
 *   <li><b>Need</b> — whatever it wants, it can only be as large as the surrounding blocks allow.
 *       {@link #largestFittingScale} walks candidate sizes down from what it wants and takes the
 *       first whose box is actually free, so growth is only ever toward a size already proven to
 *       fit and the creature can never inflate itself into a wall.</li>
 * </ul>
 *
 * <p>Shrinking is {@link #SHRINK_URGENCY}× faster than growing: being boxed in is a need and it
 * reacts at once, while swelling is a display. If not even {@code minScale} fits — a player has
 * walled it in — it holds at {@code minScale} rather than forcing; from there it is in vanilla's
 * suffocation, the same as any other mob in a wall.
 */
public record OccamyChoranaptyxis(float minScale, float maxScale, float calmScale, float rate)
        implements CreatureAbility {

    public static final MapCodec<OccamyChoranaptyxis> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("min_scale", 0.35f).forGetter(OccamyChoranaptyxis::minScale),
            Codec.FLOAT.optionalFieldOf("max_scale", 2.2f).forGetter(OccamyChoranaptyxis::maxScale),
            Codec.FLOAT.optionalFieldOf("calm_scale", 1.0f).forGetter(OccamyChoranaptyxis::calmScale),
            Codec.FLOAT.optionalFieldOf("rate", 0.06f).forGetter(OccamyChoranaptyxis::rate)
    ).apply(instance, OccamyChoranaptyxis::new));

    /** Ticks between size decisions. A body does not need to be re-measured every tick. */
    private static final int INTERVAL = 4;

    /** How many candidate sizes {@link #largestFittingScale} tries between what it wants and its floor. */
    public static final int PROBE_STEPS = 8;

    /** Shrinking is a need, growing is a want, so shrinking moves this much faster. */
    public static final float SHRINK_URGENCY = 3.0f;

    /**
     * A body of this scale would fit where the creature is standing. Kept as an interface so
     * {@link #largestFittingScale} can be tested without a level.
     */
    @FunctionalInterface
    public interface FitProbe {
        boolean fits(float scale);
    }

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.OCCAMY_CHORANAPTYXIS;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide() || entity.tickCount % INTERVAL != 0) {
            return;
        }
        float floor = Math.min(minScale, maxScale);
        float wanted = Mth.clamp(entity.getTarget() != null ? maxScale : calmScale, floor, maxScale);
        float fitted = largestFittingScale(floor, wanted, PROBE_STEPS, scale -> fits(entity, scale));

        float current = entity.getSizeScale();
        float step = fitted < current ? rate * SHRINK_URGENCY : rate;
        float next = approach(current, fitted, step);
        if (Math.abs(next - current) < 1.0e-4f) {
            return;
        }
        entity.applySizeScale(next);
        // The path it is walking was solved for a different body; a new width needs a new path.
        if (Mth.floor(current * entity.getType().getDimensions().width())
                != Mth.floor(next * entity.getType().getDimensions().width())) {
            entity.getNavigation().recomputePath();
        }
    }

    /**
     * The largest scale in {@code [floor, wanted]} the probe accepts, tried from {@code wanted}
     * downward over {@code steps} candidates. Falls back to {@code floor} when nothing fits, which is
     * the smallest the creature is willing to be rather than a claim that it fits.
     */
    public static float largestFittingScale(float floor, float wanted, int steps, @NonNull FitProbe probe) {
        if (wanted <= floor) {
            return floor;
        }
        float span = wanted - floor;
        for (int i = 0; i < steps; i++) {
            float candidate = wanted - span * i / steps;
            if (probe.fits(candidate)) {
                return candidate;
            }
        }
        return floor;
    }

    /** Would a body of this scale stand free where the creature is? Blocks and entities both count. */
    private static boolean fits(GenericBeastEntity entity, float scale) {
        AABB box = entity.getType().getDimensions().scale(scale).makeBoundingBox(entity.position());
        return entity.level().noCollision(entity, box);
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }
}
