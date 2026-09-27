package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerTreasure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Digging for treasure: a Niffler that smells gold in the ground goes and gets it.
 *
 * <p>The rules, all of them strict:
 * <ul>
 *   <li><b>What:</b> a natural deposit ({@link NifflerTreasure#ORE} — gold, diamond and emerald ores, amethyst) that is
 *       either open to the air or lies under at most {@link #MAX_BURROW} blocks of loose ground
 *       ({@link NifflerTreasure#SOFT}), which it burrows through first. Never a block a player builds with.</li>
 *   <li><b>Where:</b> within {@link #RADIUS} blocks sideways and {@link #BELOW} down of where it stands; only where
 *       mobs may grief ({@link EventHooks#canEntityGrief} — the {@code mobGriefing} rule and any protection mod).</li>
 *   <li><b>How often:</b> a small box looked over every {@link #SCAN_INTERVAL} ticks, a {@link #COOLDOWN} after every
 *       dig, and a spot it could not reach is not tried again.</li>
 *   <li><b>What it gets:</b> the block is really mined — gone — and its drops go into the pouch, the rest left on the
 *       ground. (It used to roll the block's loot and leave the block standing: a Niffler beside a gold block made gold
 *       for ever.)</li>
 * </ul>
 */
public class NifflerSeekShinyBlockGoal extends Goal {

    public static final int RADIUS = 6;
    public static final int BELOW = 3;
    public static final int MAX_BURROW = 2;
    public static final int SCAN_INTERVAL = 100;
    public static final int COOLDOWN = 600;
    public static final int DIG_TICKS = 40;
    /** Close enough to dig: within about two blocks of the top of the column. */
    private static final double REACH_SQR = 4.5;

    private final NifflerEntity niffler;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private final Deque<BlockPos> dig = new ArrayDeque<>();
    private @Nullable BlockPos standAt;
    private int nextScan;
    private int digTicks;

    public NifflerSeekShinyBlockGoal(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (niffler.isCarried() || niffler.isPouchFull() || niffler.digCooldown() > 0 || --nextScan > 0
                || !(niffler.level() instanceof ServerLevel level) || !EventHooks.canEntityGrief(level, niffler)) {
            return false;
        }
        nextScan = SCAN_INTERVAL + niffler.getRandom().nextInt(40);
        return plan(level);
    }

    @Override
    public boolean canContinueToUse() {
        return !dig.isEmpty() && !niffler.isCarried() && niffler.level() instanceof ServerLevel level
                && EventHooks.canEntityGrief(level, niffler);
    }

    @Override
    public void start() {
        digTicks = 0;
        niffler.onSpottedTreasure();
    }

    @Override
    public void stop() {
        dig.clear();
        standAt = null;
        digTicks = 0;
        niffler.setDigging(false);
        niffler.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos next = dig.peek();
        if (next == null || !(niffler.level() instanceof ServerLevel level)) return;
        Vec3 center = Vec3.atCenterOf(next);
        niffler.getLookControl().setLookAt(center.x, center.y, center.z, 30f, 30f);
        if (standAt != null && niffler.position().distanceToSqr(Vec3.atBottomCenterOf(standAt)) > REACH_SQR) {
            if (niffler.getNavigation().isDone()) {
                if (!niffler.getNavigation().moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 1.1)) {
                    giveUp(next);
                }
            }
            return;
        }
        niffler.getNavigation().stop();
        niffler.setDigging(true);
        BlockState state = level.getBlockState(next);
        if (!diggable(state)) {
            dig.poll();   // someone else took it, or it changed; move on
            digTicks = 0;
            return;
        }
        if (digTicks % 10 == 0) {
            niffler.digEffects(next, state);
        }
        if (++digTicks >= DIG_TICKS) {
            digTicks = 0;
            dig.poll();
            niffler.mine(level, next, state);
            if (dig.isEmpty()) {
                niffler.startDigCooldown(COOLDOWN);
            }
        }
    }

    private void giveUp(BlockPos pos) {
        unreachable.add(pos.immutable());
        dig.clear();
    }

    private static boolean diggable(BlockState state) {
        return state.is(NifflerTreasure.ORE) || state.is(NifflerTreasure.SOFT);
    }

    /** Finds the nearest reachable deposit and the column it must burrow down to get it. */
    boolean plan(ServerLevel level) {
        BlockPos origin = niffler.blockPosition();
        BlockPos best = null;
        Deque<BlockPos> bestColumn = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RADIUS, -BELOW, -RADIUS), origin.offset(RADIUS, 1, RADIUS))) {
            if (!level.getBlockState(pos).is(NifflerTreasure.ORE) || unreachable.contains(pos)) {
                continue;
            }
            Deque<BlockPos> column = column(level, pos.immutable());
            double d = origin.distSqr(pos);
            if (column != null && d < bestDist) {
                bestDist = d;
                best = pos.immutable();
                bestColumn = column;
            }
        }
        if (best == null) {
            return false;
        }
        // Walk to the top of the column. Reachability is proved by the walk itself: a path that cannot be made
        // blacklists the spot (giveUp), rather than paying for a path search on every candidate here.
        dig.clear();
        dig.addAll(bestColumn);
        standAt = bestColumn.peekFirst().above();
        return true;
    }

    /**
     * The blocks to dig, top down, ending with the ore: the ore alone if a side is open to the air, else the loose
     * ground straight above it (at most {@link #MAX_BURROW}) up to open air. {@code null} if it cannot be reached.
     */
    static @Nullable Deque<BlockPos> column(ServerLevel level, BlockPos ore) {
        Deque<BlockPos> column = new ArrayDeque<>();
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(ore.relative(dir)).isAir()) {
                column.add(ore);
                return column;
            }
        }
        BlockPos above = ore.above();
        for (int i = 0; i < MAX_BURROW; i++) {
            BlockState state = level.getBlockState(above);
            if (state.isAir()) {
                column.addLast(ore);
                return column;
            }
            if (!state.is(NifflerTreasure.SOFT)) {
                return null;
            }
            column.addFirst(above);
            above = above.above();
        }
        if (!level.getBlockState(above).isAir()) {
            return null;
        }
        column.addLast(ore);
        return column;
    }

    /** Test/debug hook: the planned dig, top first. */
    public Deque<BlockPos> plannedDig() {
        return dig;
    }
}
