package at.koopro.wizardsandbeasts.creature.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * When a day or night creature may appear on its own.
 *
 * <p>The placement predicates used to read {@code getRawBrightness(pos, 0)}. That is the <em>stored</em> sky light,
 * which is 15 under open sky at noon and at midnight alike. So "night" creatures could only pass in caves or under a
 * thick canopy, and "day" creatures spawned under the stars (Beast Renaissance D2). The answer has two parts:
 * <ul>
 *   <li>the time: whether it is dark outside in this level now;</li>
 *   <li>the spot: the local light with the sky dimmed by that time, so a lit cave is not "day" and a lantern-lit
 *       yard is not "night".</li>
 * </ul>
 */
public final class SpawnTime {

    /** Light at or above this is a lit spot; at or below it, a dark one (vanilla's own monster/animal line). */
    public static final int LIGHT_LINE = 7;

    private SpawnTime() {}

    /** The rule itself, free of the world so it can be tested. */
    public static boolean fits(boolean daytimeCreature, boolean darkOutside, int localLight) {
        return daytimeCreature
                ? !darkOutside && localLight >= LIGHT_LINE
                : darkOutside && localLight <= LIGHT_LINE;
    }

    /** {@link #fits} for a candidate spawn position. */
    public static boolean fits(boolean daytimeCreature, ServerLevelAccessor accessor, BlockPos pos) {
        ServerLevel level = accessor.getLevel();
        int localLight = accessor.getMaxLocalRawBrightness(pos, level.getSkyDarken());
        return fits(daytimeCreature, level.isDarkOutside(), localLight);
    }
}
