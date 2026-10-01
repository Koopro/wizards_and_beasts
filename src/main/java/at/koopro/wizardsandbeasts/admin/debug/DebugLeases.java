package at.koopro.wizardsandbeasts.admin.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.command.debug.DebugModeService;
import at.koopro.wizardsandbeasts.network.debug.DebugModeS2CPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Debug tools switched on from the Control Center are <em>leased</em>, never left on: each one is reverted when the
 * administrator who switched it on closes the Control Center (the client sends a release), logs out, when the server
 * stops, or after {@link #TTL_MILLIS} without the open panel renewing it. Turning a tool off needs no lease.
 *
 * <p>Per-administrator tools (their own debug mode) go back to off. Server-wide tools (debug mode for everyone, spell
 * logging for everyone, the mod's log level) go back to the value they had before the first administrator changed
 * them, once the last administrator holding them has let go. The {@code /wandb debug} commands are untouched: a debug
 * mode switched on by command is not the panel's to revert.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DebugLeases {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** A lease not renewed for this long is released (the panel renews while the Control Center is open). */
    public static final long TTL_MILLIS = 5 * 60_000L;
    private static final int EXPIRY_CHECK_TICKS = 100;

    public enum Tool {
        /** The administrator's own debug mode: the in-world inspector beside whatever they look at, their spell log. */
        MY_DEBUG_MODE,
        /** Debug mode for every player. */
        ALL_DEBUG_MODE,
        /** Every player's cast events logged at INFO, not only those in debug mode. */
        SPELL_LOGGING,
        /** The level of this mod's loggers ({@link ModLogLevel}). */
        LOG_LEVEL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What happened. {@code code}: {@code ok}, {@code unauthorized}, {@code panel_only}, {@code invalid_value}. */
    public record Outcome(boolean success, String code) {}

    /** The tools as they are now, and which of them this administrator holds a lease on. */
    public record State(boolean myDebugMode, boolean allDebugMode, boolean spellLogging, ModLogLevel.Choice logLevel,
                        Set<Tool> held) {}

    /** Who holds what, for the live view. */
    public record Holding(UUID admin, Set<Tool> tools, long idleMillis) {}

    private static final class Lease {
        final Set<Tool> held = EnumSet.noneOf(Tool.class);
        long renewedAt = System.currentTimeMillis();
    }

    /** A server-wide value with the value to put back and the administrators still holding it changed. */
    private static final class Shared<T> {
        private final Supplier<T> read;
        private final Consumer<T> write;
        private @Nullable T baseline;
        private final Set<UUID> holders = new HashSet<>();

        Shared(Supplier<T> read, Consumer<T> write) {
            this.read = read;
            this.write = write;
        }

        /** Sets the value; holds a lease unless it is the value from before anyone changed it. */
        boolean set(UUID admin, T value) {
            if (holders.isEmpty()) {
                baseline = read.get();
            }
            write.accept(value);
            if (value.equals(baseline)) {
                holders.clear();
                baseline = null;
                return false;
            }
            holders.add(admin);
            return true;
        }

        void release(UUID admin) {
            if (holders.remove(admin) && holders.isEmpty() && baseline != null) {
                write.accept(baseline);
                baseline = null;
            }
        }

        void releaseAll() {
            if (!holders.isEmpty() && baseline != null) {
                write.accept(baseline);
            }
            holders.clear();
            baseline = null;
        }
    }

    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static final Shared<Boolean> ALL_DEBUG = new Shared<>(DebugModeService::isGlobalEnabled, DebugModeService::setGlobal);
    private static final Shared<Boolean> SPELL_LOG = new Shared<>(DebugHooks::spellLoggingForAll, DebugHooks::setSpellLoggingForAll);
    /** The log level is restored from its full baseline (including whether a logger entry existed), not a choice. */
    private static ModLogLevel.@Nullable Baseline logBaseline;
    private static final Set<UUID> LOG_HOLDERS = new HashSet<>();

    private DebugLeases() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.DEBUG);
    }

    /** Switches one tool. {@code value}: {@code true}/{@code false}, or a {@link ModLogLevel.Choice} id for the log level. */
    public static synchronized Outcome set(AdminContext actor, MinecraftServer server, Tool tool, String value) {
        if (!authorised(actor)) {
            LOGGER.warn("[Admin] Refused debug tool {} from unauthorised {}", tool.id(), actor.actorName());
            return new Outcome(false, "unauthorized");
        }
        UUID admin = actor.actorId();
        if (admin == null) {
            // A lease needs someone whose leaving ends it. The console uses /wandb debug.
            return new Outcome(false, "panel_only");
        }
        Lease lease = LEASES.computeIfAbsent(admin, id -> new Lease());
        lease.renewedAt = System.currentTimeMillis();
        switch (tool) {
            case MY_DEBUG_MODE, ALL_DEBUG_MODE, SPELL_LOGGING -> {
                if (!value.equals("true") && !value.equals("false")) {
                    return new Outcome(false, "invalid_value");
                }
                boolean on = value.equals("true");
                if (tool == Tool.MY_DEBUG_MODE) {
                    boolean had = DebugModeService.hasOwn(admin);
                    DebugModeService.setForPlayer(admin, on);
                    if (on && !had) {
                        lease.held.add(tool);
                    } else if (!on) {
                        lease.held.remove(tool);
                    }
                } else {
                    Shared<Boolean> shared = tool == Tool.ALL_DEBUG_MODE ? ALL_DEBUG : SPELL_LOG;
                    if (shared.set(admin, on)) {
                        lease.held.add(tool);
                    } else {
                        // Back at the value from before: nobody holds it any more.
                        LEASES.values().forEach(other -> other.held.remove(tool));
                    }
                }
            }
            case LOG_LEVEL -> {
                ModLogLevel.Choice choice = ModLogLevel.Choice.byId(value);
                if (choice == null) {
                    return new Outcome(false, "invalid_value");
                }
                if (LOG_HOLDERS.isEmpty()) {
                    logBaseline = ModLogLevel.baseline();
                }
                ModLogLevel.set(choice);
                if (logBaseline != null && ModLogLevel.Choice.of(logBaseline.level()) == choice) {
                    ModLogLevel.restore(logBaseline);
                    logBaseline = null;
                    LOG_HOLDERS.clear();
                    LEASES.values().forEach(other -> other.held.remove(tool));
                } else {
                    LOG_HOLDERS.add(admin);
                    lease.held.add(tool);
                }
            }
        }
        if (tool == Tool.MY_DEBUG_MODE || tool == Tool.ALL_DEBUG_MODE) {
            syncDebugMode(server);
        }
        LOGGER.info("[Admin] {} set debug tool {} to {}", actor.actorName(), tool.id(), value);
        dropIfEmpty(admin);
        return new Outcome(true, "ok");
    }

    /** Everything {@code admin} holds goes back. Called when their Control Center closes, on logout and on expiry. */
    public static synchronized void release(UUID admin, @Nullable MinecraftServer server) {
        Lease lease = LEASES.remove(admin);
        if (lease == null) {
            return;
        }
        boolean debugChanged = false;
        if (lease.held.contains(Tool.MY_DEBUG_MODE)) {
            DebugModeService.setForPlayer(admin, false);
            debugChanged = true;
        }
        if (lease.held.contains(Tool.ALL_DEBUG_MODE)) {
            ALL_DEBUG.release(admin);
            debugChanged = true;
        }
        SPELL_LOG.release(admin);
        if (LOG_HOLDERS.remove(admin) && LOG_HOLDERS.isEmpty() && logBaseline != null) {
            ModLogLevel.restore(logBaseline);
            logBaseline = null;
        }
        if (!lease.held.isEmpty()) {
            LOGGER.info("[Admin] Debug tools of {} released: {}", admin, lease.held);
        }
        if (debugChanged && server != null) {
            syncDebugMode(server);
        }
    }

    /** Every lease goes back: server stop, so nothing carries into the next world in this JVM (the log level would). */
    public static synchronized void releaseAll(@Nullable MinecraftServer server) {
        for (UUID admin : new ArrayList<>(LEASES.keySet())) {
            release(admin, server);
        }
        ALL_DEBUG.releaseAll();
        SPELL_LOG.releaseAll();
        if (logBaseline != null) {
            ModLogLevel.restore(logBaseline);
            logBaseline = null;
        }
        LOG_HOLDERS.clear();
    }

    /** The open panel saying it is still there. */
    public static synchronized void renew(UUID admin) {
        Lease lease = LEASES.get(admin);
        if (lease != null) {
            lease.renewedAt = System.currentTimeMillis();
        }
    }

    public static synchronized State state(@Nullable UUID admin) {
        Lease lease = admin == null ? null : LEASES.get(admin);
        return new State(admin != null && DebugModeService.hasOwn(admin), DebugModeService.isGlobalEnabled(),
                DebugHooks.spellLoggingForAll(), ModLogLevel.current(),
                lease == null ? Set.of() : Set.copyOf(lease.held));
    }

    public static synchronized List<Holding> holdings() {
        long now = System.currentTimeMillis();
        List<Holding> out = new ArrayList<>();
        LEASES.forEach((admin, lease) -> {
            if (!lease.held.isEmpty()) {
                out.add(new Holding(admin, Set.copyOf(lease.held), now - lease.renewedAt));
            }
        });
        return out;
    }

    /** Releases leases whose panel stopped renewing them by {@code now}; returns how many. Run every 100 ticks. */
    public static synchronized int expire(long now, @Nullable MinecraftServer server) {
        List<UUID> stale = new ArrayList<>();
        LEASES.forEach((admin, lease) -> {
            if (now - lease.renewedAt > TTL_MILLIS) {
                stale.add(admin);
            }
        });
        stale.forEach(admin -> release(admin, server));
        return stale.size();
    }

    private static void dropIfEmpty(UUID admin) {
        Lease lease = LEASES.get(admin);
        if (lease != null && lease.held.isEmpty()) {
            LEASES.remove(admin);
        }
    }

    private static void syncDebugMode(MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            DebugModeS2CPayload.sendTo(online, DebugModeService.isEnabled(online));
        }
    }

    // ── cleanup ──

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player.getUUID(), player.level().getServer());
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        releaseAll(event.getServer());
        CastDiagnostics.clear();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % EXPIRY_CHECK_TICKS == 0) {
            int expired = expire(System.currentTimeMillis(), event.getServer());
            if (expired > 0) {
                LOGGER.info("[Admin] {} debug lease(s) expired without the panel renewing them", expired);
            }
        }
    }
}
