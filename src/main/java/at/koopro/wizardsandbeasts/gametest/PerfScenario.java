package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A load scenario for documentation/PERFORMANCE_AUDIT.md, not a correctness test.
 *
 * <p>Registered only when the JVM runs with {@code -Dwandb.perf=true}, and then <em>instead of</em> every other
 * scenario, so the server is doing nothing else: a walled 33x33 arena, one of every registered creature (twice over),
 * three players ticked like connected ones. After {@link #WARMUP_TICKS} it samples the server's own tick-time ring
 * ({@code MinecraftServer.getTickTimesNanos}) every 100 ticks for {@link #MEASURE_TICKS} and logs the mean and
 * 95th-percentile milliseconds per tick as a {@code WANDB_PERF} line. Run with Java Flight Recorder attached for the
 * per-method picture.
 */
public final class PerfScenario {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String PROPERTY = "wandb.perf";
    public static final int WARMUP_TICKS = 300;
    public static final int MEASURE_TICKS = 1200;
    /** Copies of each creature; {@code -Dwandb.perf.copies=N} scales the load for steadier profiles. */
    public static final int COPIES = Integer.getInteger("wandb.perf.copies", 2);
    /** Half the arena's width; {@code -Dwandb.perf.half=N} spreads a larger load out (default 16 → 33×33). */
    private static final int HALF = Integer.getInteger("wandb.perf.half", 16);

    private PerfScenario() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("perf_creature_arena", "performance load scenario (not a correctness test)", PerfScenario::arena);
    }

    private static void arena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        // Force the arena's chunks to entity-ticking. Without this most of the arena lies outside the test's ticking
        // area and its creatures are never ticked at all (found 2026-10-07: tickCount 0 for most of the roster), so
        // the scenario measured a fraction of the load it claimed to. Released when the run ends.
        List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();
        for (int cx = (origin.getX() - HALF) >> 4; cx <= (origin.getX() + HALF) >> 4; cx++) {
            for (int cz = (origin.getZ() - HALF) >> 4; cz <= (origin.getZ() + HALF) >> 4; cz++) {
                if (level.setChunkForced(cx, cz, true)) {
                    forced.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                }
            }
        }
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                for (int y = 0; y < 4; y++) {
                    level.setBlock(origin.offset(x, y, z),
                            (edge ? Blocks.GLASS : Blocks.AIR).defaultBlockState(), 2);
                }
            }
        }

        int spawned = 0;
        int i = 0;
        for (var holder : ModCreatures.ENTITIES.values()) {
            EntityType<GenericBeastEntity> type = holder.get();
            for (int copy = 0; copy < COPIES; copy++) {
                int x = (i * 7) % (2 * HALF - 3) - HALF + 2;
                int z = (i * 13) % (2 * HALF - 3) - HALF + 2;
                i++;
                GenericBeastEntity beast = type.create(level, EntitySpawnReason.EVENT);
                if (beast == null) {
                    continue;
                }
                beast.snapTo(origin.getX() + x + 0.5, origin.getY(), origin.getZ() + z + 0.5, 0f, 0f);
                level.addFreshEntity(beast);
                spawned++;
            }
        }

        List<ServerPlayer> players = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "PerfPlayer" + p, GameType.CREATIVE);
            player.teleportTo(origin.getX() + 0.5 + p * 4, origin.getY(), origin.getZ() + 0.5);
            players.add(player);
        }

        int creatures = spawned;
        int[] tick = {0};
        List<Long> samples = new ArrayList<>();
        helper.onEachTick(() -> {
            tick[0]++;
            for (ServerPlayer player : players) {
                player.doTick();
            }
            int measured = tick[0] - WARMUP_TICKS;
            if (measured > 0 && measured % 100 == 0 && measured <= MEASURE_TICKS) {
                for (long nanos : server.getTickTimesNanos()) {
                    samples.add(nanos);
                }
            }
            if (measured == MEASURE_TICKS) {
                samples.sort(Long::compare);
                double mean = samples.stream().mapToLong(Long::longValue).average().orElse(0) / 1.0e6;
                double p95 = samples.get((int) (samples.size() * 0.95)) / 1.0e6;
                double p99 = samples.get((int) (samples.size() * 0.99)) / 1.0e6;
                double max = samples.get(samples.size() - 1) / 1.0e6;
                List<? extends GenericBeastEntity> living = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest
                        .forClass(GenericBeastEntity.class), e -> e.isAlive());
                // Only creatures that were actually ticked run any AI; a load outside ticking chunks measures nothing.
                // A creature ticked through the whole run has a tickCount of at least the run's length.
                long ticking = living.stream().filter(e -> e.tickCount >= (WARMUP_TICKS + MEASURE_TICKS) * 9 / 10).count();
                LOGGER.info(String.format(java.util.Locale.ROOT,
                        "WANDB_PERF creatures=%d living=%d ticking=%d players=%d arena=%d mean_mspt=%.3f p95_mspt=%.3f "
                                + "p99_mspt=%.3f max_mspt=%.3f samples=%d",
                        creatures, living.size(), ticking, players.size(), 2 * HALF + 1, mean, p95, p99, max,
                        samples.size()));
                LOGGER.info("WANDB_AI " + at.koopro.wizardsandbeasts.creature.schedule.AiMetrics.summary());
                players.forEach(p -> WizardTestSupport.retire(helper, p));
                forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
                helper.succeed();
            }
        });
    }
}
