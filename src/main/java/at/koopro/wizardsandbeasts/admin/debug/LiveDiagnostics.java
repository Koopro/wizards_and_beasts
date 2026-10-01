package at.koopro.wizardsandbeasts.admin.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Debug section's live view, read on request from the systems that own each piece: beams being channelled
 * ({@link WandBeamChannelLogic}), open wand holds ({@link WandCastSessions}), the last cast events and refusal counts
 * ({@link CastDiagnostics}), this mod's entities by type, every module's state, each connection's packet rate and
 * latency, and which administrator holds which debug tool. Nothing is kept here.
 */
@NullMarked
public final class LiveDiagnostics {

    public static final int RECENT_CASTS = 32;
    public static final int COUNT_KINDS = 16;
    public static final int ENTITY_TYPES = 16;

    private LiveDiagnostics() {}

    public record Beam(String player, String spell, int ticks, boolean hasTarget) {}

    /**
     * An open wand hold.
     *
     * @param ageTicks game ticks since the hold began
     */
    public record Session(String player, long id, String spell, long ageTicks, boolean releaseConsumed,
                          boolean vanillaRelease, boolean clashHold) {}

    public record Count(String key, int count) {}

    public record ModuleRow(String module, String state) {}

    public record Connection(String player, int latencyMs, float sentPerSecond, float receivedPerSecond) {}

    public record Holding(String admin, String tools, long idleSeconds) {}

    public record Snapshot(List<Beam> beams, List<Session> sessions, List<CastDiagnostics.Event> recentCasts,
                           List<Count> castCounts, List<Count> entityTypes, int modEntities, List<ModuleRow> modules,
                           List<Connection> connections, List<Holding> holdings) {}

    public static Snapshot collect(MinecraftServer server) {
        List<Beam> beams = new ArrayList<>();
        for (WandBeamChannelLogic.ChannelView channel : WandBeamChannelLogic.channels()) {
            beams.add(new Beam(name(server, channel.player()), channel.spellId() == null ? "" : channel.spellId(),
                    channel.beamTicks(), channel.hasTarget()));
        }
        long now = server.overworld().getGameTime();
        List<Session> sessions = new ArrayList<>();
        WandCastSessions.openSessions().forEach((player, session) -> sessions.add(new Session(name(server, player),
                session.id(), session.spellId() == null ? "" : session.spellId(), Math.max(0, now - session.startGameTick()),
                session.releaseConsumed(), session.vanillaReleaseObserved(), session.clashHold())));

        List<Count> castCounts = CastDiagnostics.counts(COUNT_KINDS).stream()
                .map(entry -> new Count(entry.getKey(), entry.getValue())).toList();

        Map<String, Integer> byType = new HashMap<>();
        int modEntities = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (WizardsAndBeastsMod.MODID.equals(type.getNamespace())) {
                    byType.merge(type.getPath(), 1, Integer::sum);
                    modEntities++;
                }
            }
        }
        List<Count> entityTypes = byType.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(ENTITY_TYPES).map(entry -> new Count(entry.getKey(), entry.getValue())).toList();

        List<ModuleRow> modules = new ArrayList<>();
        for (Module module : Module.values()) {
            modules.add(new ModuleRow(ModuleIds.of(module).getPath(), ModuleManager.state(module).getSerializedName()));
        }

        List<Connection> connections = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            connections.add(new Connection(player.getName().getString(), player.connection.latency(),
                    player.connection.getConnection().getAverageSentPackets(),
                    player.connection.getConnection().getAverageReceivedPackets()));
        }

        List<Holding> holdings = new ArrayList<>();
        for (DebugLeases.Holding holding : DebugLeases.holdings()) {
            holdings.add(new Holding(name(server, holding.admin()),
                    String.join(", ", holding.tools().stream().map(DebugLeases.Tool::id).sorted().toList()),
                    holding.idleMillis() / 1000));
        }
        return new Snapshot(beams, sessions, CastDiagnostics.recent(RECENT_CASTS), castCounts, entityTypes, modEntities,
                modules, connections, holdings);
    }

    private static String name(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player == null ? id.toString().substring(0, 8) : player.getName().getString();
    }
}
