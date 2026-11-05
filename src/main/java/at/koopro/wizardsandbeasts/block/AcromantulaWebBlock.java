package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.entity.creature.AcromantulaEntity;
import at.koopro.wizardsandbeasts.entity.creature.AcromantulaWebs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Acromantula silk: a vanilla cobweb that its spinners walk through and that does not outlast them.
 *
 * <p>Placed only by Acromantulas — a snare flung at a target, and the webbing they spin around their colony's home
 * ({@code AcromantulaWebs}). It slows and holds everything else exactly as a cobweb does.
 *
 * <p>Cleanup without bookkeeping: on each random tick, silk with no living Acromantula within
 * {@link #KEEPER_RADIUS} crumbles. A nest stays webbed while its colony lives; a snare left in a field is gone within
 * minutes of the spider leaving; nothing needs to remember where webs were put, and a server restart loses nothing.
 */
public class AcromantulaWebBlock extends WebBlock {

    /**
     * Its colony keeps it from anywhere in the colony's ground: the territory plus the nest around the home. At 12 the
     * nest silk crumbled whenever the colony went hunting at the edge of its 20-block territory (Beast Renaissance
     * D15).
     */
    public static final double KEEPER_RADIUS = AcromantulaEntity.TERRITORY + AcromantulaWebs.NEST_RADIUS;

    public AcromantulaWebBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects, boolean intersects) {
        if (entity instanceof AcromantulaEntity) {
            return;   // its own silk
        }
        super.entityInside(state, level, pos, entity, effects, intersects);
    }

    @Override
    protected boolean isRandomlyTicking(@NonNull BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                              @NonNull RandomSource random) {
        // Only judged once every chunk a keeper could stand in has its entities loaded: a random tick can come before
        // a chunk's creatures are back after a reload, and the nest then crumbled with its colony right there.
        if (keepersLoaded(level, pos) && !kept(level, pos)) {
            level.removeBlock(pos, false);
        }
    }

    private static boolean keepersLoaded(ServerLevel level, BlockPos pos) {
        int reach = (int) Math.ceil(KEEPER_RADIUS);
        int minX = (pos.getX() - reach) >> 4;
        int maxX = (pos.getX() + reach) >> 4;
        int minZ = (pos.getZ() - reach) >> 4;
        int maxZ = (pos.getZ() + reach) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a living Acromantula is near enough to keep this silk. */
    public static boolean kept(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(AcromantulaEntity.class, new AABB(pos).inflate(KEEPER_RADIUS),
                Entity::isAlive).isEmpty();
    }
}
