package at.koopro.wizardsandbeasts.creature.ecology;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where things died lately, per level, for scavengers to find. A death writes one slot of a fixed ring (no query, no
 * allocation after the first); a scavenger reads the ring during its own look around (the MEDIUM tier) — at most
 * {@link #SIZE} distance checks. So a mob farm's deaths cost a few array writes, and only scavengers pay to read.
 */
@NullMarked
public final class CarrionLog {

    public static final int SIZE = 64;

    private final double[] x = new double[SIZE];
    private final double[] y = new double[SIZE];
    private final double[] z = new double[SIZE];
    private final long[] tick = new long[SIZE];
    private int next;

    private static final Map<ServerLevel, CarrionLog> LOGS = new WeakHashMap<>();

    static synchronized CarrionLog of(ServerLevel level) {
        return LOGS.computeIfAbsent(level, l -> new CarrionLog());
    }

    /** Something died at {@code at} on {@code now}. */
    synchronized void record(Vec3 at, long now) {
        x[next] = at.x;
        y[next] = at.y;
        z[next] = at.z;
        tick[next] = now;
        next = (next + 1) % SIZE;
    }

    /**
     * The nearest death within {@code radius} of {@code here} younger than {@code maxAge} ticks, skipping one within
     * two blocks of {@code eaten} (the remains it has already been over), or {@code null}.
     */
    synchronized @Nullable Vec3 nearest(Vec3 here, double radius, long now, long maxAge, @Nullable Vec3 eaten) {
        double best = radius * radius;
        int found = -1;
        for (int i = 0; i < SIZE; i++) {
            if (tick[i] == 0 || now - tick[i] > maxAge) {
                continue;
            }
            double dx = x[i] - here.x, dy = y[i] - here.y, dz = z[i] - here.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d > best) {
                continue;
            }
            if (eaten != null && eaten.distanceToSqr(x[i], y[i], z[i]) < 4.0) {
                continue;
            }
            best = d;
            found = i;
        }
        return found < 0 ? null : new Vec3(x[found], y[found], z[found]);
    }
}
