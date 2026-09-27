package at.koopro.wizardsandbeasts.spell.patronus;

import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a Patronus holds Dementors off, and how far.
 *
 * <p>Strength decides the reach. A corporeal Patronus (power at or above {@link PatronusEntity#CORPOREAL_POWER}) is
 * the thing that drives a swarm away — Harry's stag cleared the lake shore — so it wards a wide sphere that grows with
 * power. A non-corporeal mist only shields the small space around itself, the way Harry's early wisps bought him a
 * moment and no more. Everything here is server-side: {@code patronusPower} is never synced.
 *
 * <p>Consumers: the Dementor's senses (a warded creature cannot be felt), its aura, its flee goal and its Kiss.
 */
public final class PatronusDetection {

    /** How far a non-corporeal mist wards. */
    public static final double MIST_REPEL_RADIUS = 5.0;
    /** How far a corporeal Patronus at exactly {@link PatronusEntity#CORPOREAL_POWER} wards. */
    public static final double CORPOREAL_REPEL_RADIUS = 12.0;
    /** The widest ward any Patronus casts. */
    public static final double MAX_REPEL_RADIUS = 24.0;

    private PatronusDetection() {}

    /** The ward radius of a Patronus of this form and power. */
    public static double repelRadius(boolean corporeal, float power) {
        if (!corporeal) {
            return MIST_REPEL_RADIUS;
        }
        double extra = Math.max(0.0, power - PatronusEntity.CORPOREAL_POWER) * 0.2;
        return Math.min(MAX_REPEL_RADIUS, CORPOREAL_REPEL_RADIUS + extra);
    }

    /** The ward radius of this Patronus. */
    public static double repelRadius(PatronusEntity patronus) {
        return repelRadius(patronus.isCorporeal(), patronus.getPower());
    }

    /** The nearest Patronus whose ward covers {@code pos}, or {@code null}. */
    public static @Nullable PatronusEntity wardingPatronus(Level level, Vec3 pos) {
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }
        AABB box = new AABB(pos, pos).inflate(MAX_REPEL_RADIUS);
        PatronusEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (PatronusEntity patronus : sl.getEntitiesOfClass(PatronusEntity.class, box, PatronusEntity::isAlive)) {
            double d = patronus.position().distanceToSqr(pos);
            double r = repelRadius(patronus);
            if (d <= r * r && d < bestD) {
                bestD = d;
                best = patronus;
            }
        }
        return best;
    }

    /** Whether any Patronus wards {@code pos}. */
    public static boolean isWarded(Level level, Vec3 pos) {
        return wardingPatronus(level, pos) != null;
    }
}
