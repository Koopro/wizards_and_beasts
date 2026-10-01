package at.koopro.wizardsandbeasts.admin.perf;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.spell.SpellClashEntity;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server is doing right now, read from the game on request — never estimated. A value the game does not
 * measure is reported as unavailable ({@link #UNAVAILABLE} or {@code measured=false}), and the panel says so instead of
 * showing a number.
 *
 * <p>Collected only when an administrator's open Performance page asks (at most once a second each): the entity counts
 * walk every loaded entity once, which is fine on request and wrong per tick.
 */
@NullMarked
public final class PerformanceMetrics {

    /** A metric the game does not measure in this situation. */
    public static final double UNAVAILABLE = -1;

    private PerformanceMetrics() {}

    /** One dimension. {@code msptAvg} is {@link #UNAVAILABLE} when the server keeps no per-dimension tick times. */
    public record Dimension(String id, int entities, int modEntities, int spellEntities, int loadedChunks, int players,
                            double msptAvg) {}

    /**
     * One reading.
     *
     * @param tps              ticks per second achieved: the target rate, or less when the average tick runs long;
     *                         {@link #UNAVAILABLE} before the server has timed a tick
     * @param msptAvg          average tick time over the last 100 ticks, milliseconds
     * @param msptMax          longest of those ticks
     * @param packetsSent      packets per second sent to all players (vanilla's per-connection averages, summed)
     * @param networkMeasured  false when nobody is connected, so there is no connection to measure
     */
    public record Snapshot(float targetTps, double tps, double msptAvg, double msptMax, List<Dimension> dimensions,
                           int players, int modEffectsOnPlayers, int beams, int castSessions, int beamScanInterval,
                           int beamEffectInterval, float packetsSent, float packetsReceived, double latencyAvgMs,
                           boolean networkMeasured) {

        public int entities() {
            return dimensions.stream().mapToInt(Dimension::entities).sum();
        }

        public int modEntities() {
            return dimensions.stream().mapToInt(Dimension::modEntities).sum();
        }

        public int spellEntities() {
            return dimensions.stream().mapToInt(Dimension::spellEntities).sum();
        }
    }

    public static Snapshot collect(MinecraftServer server) {
        float target = server.tickRateManager().tickrate();
        long[] times = server.getTickTimesNanos();
        long sum = 0;
        long max = 0;
        int timed = 0;
        for (long nanos : times) {
            if (nanos > 0) {
                sum += nanos;
                max = Math.max(max, nanos);
                timed++;
            }
        }
        double msptAvg = timed == 0 ? UNAVAILABLE : sum / (double) timed / 1_000_000.0;
        double msptMax = timed == 0 ? UNAVAILABLE : max / 1_000_000.0;
        // A tick that takes longer than its slot (1000 / target ms) slows the clock; a fast tick never speeds it up.
        double tps = msptAvg <= 0 ? UNAVAILABLE : Math.min(target, 1000.0 / msptAvg);

        List<Dimension> dimensions = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            int entities = 0;
            int mod = 0;
            int spells = 0;
            for (Entity entity : level.getAllEntities()) {
                entities++;
                Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (WizardsAndBeastsMod.MODID.equals(type.getNamespace())) {
                    mod++;
                }
                if (entity instanceof SpellProjectileEntity || entity instanceof SpellClashEntity) {
                    spells++;
                }
            }
            long[] dimTimes = server.getTickTime(level.dimension());
            dimensions.add(new Dimension(level.dimension().identifier().toString(), entities, mod, spells,
                    level.getChunkSource().getLoadedChunksCount(), level.players().size(), average(dimTimes)));
        }

        int modEffects = 0;
        float sent = 0;
        float received = 0;
        long latency = 0;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            for (MobEffectInstance effect : player.getActiveEffects()) {
                Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                if (id != null && WizardsAndBeastsMod.MODID.equals(id.getNamespace())) {
                    modEffects++;
                }
            }
            sent += player.connection.getConnection().getAverageSentPackets();
            received += player.connection.getConnection().getAverageReceivedPackets();
            latency += player.connection.latency();
        }
        return new Snapshot(target, tps, msptAvg, msptMax, List.copyOf(dimensions), players.size(), modEffects,
                WandBeamChannelLogic.channelCount(), WandCastSessions.openSessions().size(),
                WandBeamChannelLogic.scanIntervalTicks(), WandBeamChannelLogic.effectIntervalTicks(),
                sent, received, players.isEmpty() ? UNAVAILABLE : latency / (double) players.size(), !players.isEmpty());
    }

    private static double average(long @org.jspecify.annotations.Nullable [] times) {
        if (times == null) {
            return UNAVAILABLE;
        }
        long sum = 0;
        int timed = 0;
        for (long nanos : times) {
            if (nanos > 0) {
                sum += nanos;
                timed++;
            }
        }
        return timed == 0 ? UNAVAILABLE : sum / (double) timed / 1_000_000.0;
    }
}
