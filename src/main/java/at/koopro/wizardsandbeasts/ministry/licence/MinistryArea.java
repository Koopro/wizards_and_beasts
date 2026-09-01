package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.map.MapLandmarkTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.NullMarked;

/**
 * Where the Ministry <em>is</em>.
 *
 * <p>This mod generates no Ministry structure — none of its landmarks are generated; they are block
 * palettes players build with — so the only honest way to know you have walked into the Ministry is
 * to notice you are standing in a great deal of Ministry stone. That is exactly how the Marauder's
 * Map finds the place, and this reuses the same {@code landmark/ministry} tag rather than inventing a
 * second definition that could disagree with the map about where you are.
 *
 * <p>The consequence worth stating plainly: a player who builds their house out of Ministry marble
 * has built a Ministry area and will be asked for papers in their own kitchen. That is a fair price
 * for a rule with no region editor behind it, and it is why the penalty is a fine rather than being
 * thrown out — see {@code MinistryAreaHandler}.
 */
@NullMarked
public final class MinistryArea {

    private MinistryArea() {}

    /**
     * Counts Ministry-palette blocks in a cube of half-extent {@link LicenceRules#AREA_SCAN_RADIUS}
     * around {@code centre}.
     *
     * <p>Stops as soon as the threshold is met. A player standing in the Atrium hits it in the first
     * few dozen positions, so the common case is nowhere near the 2 197 the box could cost, and the
     * expensive case — open country — is the one that finds nothing and is only run every
     * {@link LicenceRules#AREA_CHECK_INTERVAL_TICKS} ticks.
     */
    public static int countMinistryBlocks(LevelReader level, BlockPos centre) {
        int radius = LicenceRules.AREA_SCAN_RADIUS;
        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (level.getBlockState(cursor).is(MapLandmarkTags.MINISTRY)) {
                        found++;
                        if (LicenceRules.isMinistryDensity(found)) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * True when {@code centre} is inside what the mod considers Ministry premises.
     *
     * <p>Cheap probe first. The full count is a 13³ box and this runs for every player every
     * {@link LicenceRules#AREA_CHECK_INTERVAL_TICKS} ticks, so the overwhelmingly common answer — a
     * player standing in open country, nowhere near any Ministry stone — has to be reachable without
     * two thousand block reads. {@link #hasAnyMinistryStoneUnderfoot} answers it in fifty.
     *
     * <p>The probe can only produce a false <em>negative</em>, never a false positive, and only for a
     * wizard standing inside a Ministry building on a floor made of something else within a two-block
     * drop. That is a narrow enough gap to be worth the ratio.
     */
    public static boolean isInside(LevelReader level, BlockPos centre) {
        if (!hasAnyMinistryStoneUnderfoot(level, centre)) {
            return false;
        }
        return LicenceRules.isMinistryDensity(countMinistryBlocks(level, centre));
    }

    /** The 5×5 floor slab on the two layers below {@code centre} — fifty reads, no allocation. */
    private static boolean hasAnyMinistryStoneUnderfoot(LevelReader level, BlockPos centre) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy >= -2; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (level.getBlockState(cursor).is(MapLandmarkTags.MINISTRY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
