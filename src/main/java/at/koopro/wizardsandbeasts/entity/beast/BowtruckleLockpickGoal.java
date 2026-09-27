package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastSupport;
import at.koopro.wizardsandbeasts.spell.lib.ColloportusLockStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Picking a lock, as Pickett did for Newt.
 *
 * <p>It works with the mod's one lock system, the spell lock tiers ({@link SpellCastSupport#lockTier}): a Bowtruckle's
 * two long fingers can work a <em>mechanical</em> lock — an iron door or trapdoor, the tier-2 locks that otherwise
 * want Mastered Alohomora. It cannot undo magic: a Colloportus seal ({@link ColloportusLockStore}) is not a lock to
 * fingers, and it never tries. The job is given by its bonded person ({@link BowtruckleEntity#askToPickLock}), who must
 * be allowed to touch the block themselves ({@code mayInteract} — spawn protection, claims). It walks there, works for
 * {@link #PICK_TICKS} ticks, and the lock opens (or shuts) once.
 */
public class BowtruckleLockpickGoal extends Goal {

    public static final int PICK_TICKS = 60;
    public static final double REACH_SQR = 2.25;
    public static final int SEARCH = 8;

    private final BowtruckleEntity bowtruckle;
    private int ticks;

    public BowtruckleLockpickGoal(BowtruckleEntity bowtruckle) {
        this.bowtruckle = bowtruckle;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether fingers can open this: a mechanical lock, not sealed by magic. */
    public static boolean pickable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return SpellCastSupport.lockTier(state) == 2 && !ColloportusLockStore.isLocked(level, pos)
                && state.hasProperty(BlockStateProperties.OPEN);
    }

    /** The nearest lock it could pick around {@code origin} that {@code owner} may touch, or {@code null}. */
    public static @Nullable BlockPos findLock(ServerLevel level, BlockPos origin, Player owner) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH, -2, -SEARCH), origin.offset(SEARCH, 3, SEARCH))) {
            if (pickable(level, pos) && level.mayInteract(owner, pos)) {
                double d = origin.distSqr(pos);
                if (d < bestD) {
                    bestD = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        return bowtruckle.lockJob() != null && !bowtruckle.isDefending();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        bowtruckle.setLockpicking(false);
        bowtruckle.clearLockJob();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos lock = bowtruckle.lockJob();
        if (lock == null || !(bowtruckle.level() instanceof ServerLevel level) || !pickable(level, lock)) {
            bowtruckle.clearLockJob();
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(lock);
        bowtruckle.getLookControl().setLookAt(at.x, at.y + 0.5, at.z);
        if (bowtruckle.position().distanceToSqr(at) > REACH_SQR) {
            if (bowtruckle.getNavigation().isDone()) {
                bowtruckle.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            }
            return;
        }
        bowtruckle.getNavigation().stop();
        bowtruckle.setLockpicking(true);
        if (++ticks % 15 == 0) {
            level.playSound(null, lock, ModSounds.BOWTRUCKLE_PICK.get(), SoundSource.NEUTRAL, 0.6f, 1.2f);
        }
        if (ticks >= PICK_TICKS) {
            open(level, lock);
            bowtruckle.clearLockJob();
        }
    }

    /** Works the lock once: open if shut, shut if open. */
    public static void open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, level, state, pos, !state.getValue(DoorBlock.OPEN));
        } else if (state.hasProperty(BlockStateProperties.OPEN)) {
            level.setBlockAndUpdate(pos, state.cycle(BlockStateProperties.OPEN));
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.3f);
    }
}
