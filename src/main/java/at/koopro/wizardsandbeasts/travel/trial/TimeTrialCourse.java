package at.koopro.wizardsandbeasts.travel.trial;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A broom time-trial course: a loop of hoops in the air around a broom shop, flown in order and finished back through
 * the first.
 *
 * <p>Nothing is placed in the world. The hoops are points the server checks the rider against and the rider's client
 * draws, so a course costs no blocks, leaves nothing behind, and two players can fly two courses through the same sky.
 * Generated from a seed and a terrain sampler, so the generator is a pure function the tests can run without a world.
 *
 * @param rings      hoop centres, in flying order; the finish is {@code rings.get(0)} again
 * @param ringRadius how close to a centre the rider's path must pass, in blocks
 * @param parTicks   the time to beat
 */
@NullMarked
public record TimeTrialCourse(List<Vec3> rings, double ringRadius, int parTicks) {

    /** Hoops per course, start gate included. */
    public static final int RING_COUNT = 8;
    /** Hoop radius, blocks. Generous: a hoop is a waypoint to fly through, not a needle to thread. */
    public static final double RING_RADIUS = 4.0;
    /** Course radius around the shop, blocks, before jitter. */
    public static final double COURSE_RADIUS = 56.0;
    /** How far each hoop may sit from that circle. */
    public static final double RADIAL_JITTER = 16.0;
    /** Height above the ground below a hoop, before jitter. */
    public static final int MIN_CLEARANCE = 10;
    public static final int CLEARANCE_JITTER = 14;
    /**
     * Speed par is set at, blocks per tick. Above a school broom's cruise (0.55) and below a Comet's (0.70): a
     * Cleansweep cannot make par without boosting well, a Comet makes it with a clean line, and a Nimbus has room
     * to spare. That is the whole case for a better broom, in one number.
     */
    public static final double PAR_SPEED = 0.62;

    private static final StreamCodec<ByteBuf, Vec3> VEC3_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    public static final StreamCodec<ByteBuf, TimeTrialCourse> STREAM_CODEC = StreamCodec.composite(
            VEC3_CODEC.apply(ByteBufCodecs.list(RING_COUNT * 2)), TimeTrialCourse::rings,
            ByteBufCodecs.DOUBLE, TimeTrialCourse::ringRadius,
            ByteBufCodecs.VAR_INT, TimeTrialCourse::parTicks,
            TimeTrialCourse::new);

    public TimeTrialCourse {
        rings = List.copyOf(rings);
    }

    /** Ground height at a column, the way the course generator needs it. */
    @FunctionalInterface
    public interface Terrain {
        int groundY(int x, int z);
    }

    /**
     * Lays a course around {@code centreX, centreZ}: hoops at increasing angles round a jittered circle, each a
     * jittered height above the ground beneath it and below {@code ceilingY}.
     */
    public static TimeTrialCourse generate(double centreX, double centreZ, long seed, Terrain terrain, int ceilingY) {
        Random random = new Random(seed);
        double start = random.nextDouble() * Math.PI * 2.0;
        boolean clockwise = random.nextBoolean();
        List<Vec3> rings = new ArrayList<>(RING_COUNT);
        for (int i = 0; i < RING_COUNT; i++) {
            double step = Math.PI * 2.0 / RING_COUNT;
            double angle = start + (clockwise ? -1 : 1) * (i * step + (random.nextDouble() - 0.5) * step * 0.5);
            double radius = COURSE_RADIUS + (random.nextDouble() * 2.0 - 1.0) * RADIAL_JITTER;
            double x = centreX + Math.cos(angle) * radius;
            double z = centreZ + Math.sin(angle) * radius;
            int ground = terrain.groundY(Mth.floor(x), Mth.floor(z));
            double y = Math.min(ceilingY - 4, ground + MIN_CLEARANCE + random.nextInt(CLEARANCE_JITTER + 1));
            rings.add(new Vec3(x, y, z));
        }
        return new TimeTrialCourse(rings, RING_RADIUS, parFor(rings));
    }

    /** Length of the loop, start gate to start gate, blocks. */
    public static double loopLength(List<Vec3> rings) {
        double length = 0.0;
        for (int i = 0; i < rings.size(); i++) {
            length += rings.get(i).distanceTo(rings.get((i + 1) % rings.size()));
        }
        return length;
    }

    /** Par for a loop through {@code rings}, in ticks. */
    public static int parFor(List<Vec3> rings) {
        return (int) Math.ceil(loopLength(rings) / PAR_SPEED);
    }

    /** How many hoops a finish takes: every hoop, then the start gate again. */
    public int passesToFinish() {
        return rings.size() + 1;
    }

    /** The hoop the {@code passes}-th pass must go through (0 = the start gate). */
    public Vec3 ringFor(int passes) {
        return rings.get(passes % rings.size());
    }

    /**
     * Whether a rider moving from {@code from} to {@code to} in one tick passed within {@code radius} of
     * {@code centre} — the closest point of the segment, not just its end, so a fast broom cannot skip a hoop by
     * covering it between two ticks.
     */
    public static boolean passes(Vec3 from, Vec3 to, Vec3 centre, double radius) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        double t = lengthSqr < 1.0e-9 ? 0.0 : Mth.clamp(centre.subtract(from).dot(segment) / lengthSqr, 0.0, 1.0);
        Vec3 closest = from.add(segment.scale(t));
        return closest.distanceToSqr(centre) <= radius * radius;
    }
}
