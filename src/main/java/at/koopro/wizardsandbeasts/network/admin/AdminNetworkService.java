package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.command.AdminAccess;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The server side of the Control Center's network surface: every admin packet handler lands here, and
 * every one of them builds an {@link AdminContext} from the sender before doing anything else.
 *
 * <p>This class decides nothing about settings. Reads become snapshots, writes become calls to
 * {@link AdminSettingService} — the same calls {@code /wandb admin config} makes — and the service's
 * {@link AdminResult} goes back to the sender verbatim.
 *
 * <p><b>Unauthorised senders.</b> A read request (open, section, refresh) from a non-administrator is dropped
 * with a WARN and no reply, as the module handler does: nothing about the panel is disclosed. A write request
 * is refused by the service and <em>does</em> get a REJECTED reply carrying no value — an administrator whose
 * access was revoked mid-session must see the refusal, not a row stuck on "saving".
 */
@NullMarked
public final class AdminNetworkService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AdminNetworkService() {}

    // ── reads ──

    /** Opens the Control Center on {@code player}'s screen. False (and nothing sent) when not permitted. */
    public static boolean openFor(ServerPlayer player) {
        AdminContext actor = AdminContext.of(player);
        if (!actor.canRead()) {
            LOGGER.warn("[Admin] Refused to open the Control Center for unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        PacketDistributor.sendToPlayer(player, snapshot(actor, player.level().getServer(), null, true));
        return true;
    }

    /** Resends state for one section, or for all of them when {@code section} is null. */
    public static boolean refreshFor(ServerPlayer player, @Nullable AdminCategory section) {
        AdminContext actor = AdminContext.of(player);
        if (!actor.canRead()) {
            LOGGER.warn("[Admin] Dropped admin refresh from unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        PacketDistributor.sendToPlayer(player, snapshot(actor, player.level().getServer(), section, false));
        return true;
    }

    // ── writes ──

    public static AdminResult change(ServerPlayer player, AdminChangeSettingC2SPayload request) {
        AdminResult result = AdminSettings.service().change(AdminContext.of(player), request.settingId(), request.value(),
                request.confirmed());
        reply(player, request.requestId(), result);
        return result;
    }

    public static AdminResult reset(ServerPlayer player, AdminResetSettingC2SPayload request) {
        AdminResult result = AdminSettings.service().reset(AdminContext.of(player), request.settingId(),
                request.confirmed());
        reply(player, request.requestId(), result);
        return result;
    }

    private static void reply(ServerPlayer player, int requestId, AdminResult result) {
        PacketDistributor.sendToPlayer(player, new AdminSettingResultS2CPayload(requestId, result));
    }

    /**
     * {@link AdminSettingService} observer: tells every <em>other</em> administrator online about an applied
     * change, whichever door it came through. The actor is skipped because a packet actor already has its own
     * reply, and a command actor cannot have the screen open while typing.
     */
    public static void announce(AdminContext actor, AdminResult result) {
        MinecraftServer server = actor.server();
        if (server == null || !result.applied()) {
            return;
        }
        AdminSettingResultS2CPayload payload = new AdminSettingResultS2CPayload(AdminSettingResultS2CPayload.BROADCAST, result);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (Objects.equals(online.getUUID(), actor.actorId())) {
                continue;
            }
            if (AdminAccess.allows(online.createCommandSourceStack())) {
                PacketDistributor.sendToPlayer(online, payload);
            }
        }
    }

    // ── snapshot construction ──

    public static AdminSnapshotS2CPayload snapshot(AdminContext viewer, MinecraftServer server,
                                                   @Nullable AdminCategory section, boolean openScreen) {
        AdminSettingService service = AdminSettings.service();
        List<AdminSettingDescriptor> settings = new ArrayList<>();
        for (AdminSetting<?> setting : section == null ? service.registry().all() : service.registry().inCategory(section)) {
            settings.add(AdminSettingDescriptor.of(setting, viewer));
        }
        return new AdminSnapshotS2CPayload(openScreen, section, sessionInfo(viewer, server, service), settings);
    }

    private static AdminSessionInfo sessionInfo(AdminContext viewer, MinecraftServer server, AdminSettingService service) {
        int enabled = 0;
        for (Module module : Module.values()) {
            if (ModuleManager.state(module) == ModuleState.ENABLED) {
                enabled++;
            }
        }
        List<AdminSessionInfo.RecentChange> recent = new ArrayList<>();
        for (AdminChangeRecord record : service.history().recent(AdminSessionInfo.MAX_RECENT)) {
            AdminSetting<?> setting = service.registry().get(record.settingId());
            recent.add(new AdminSessionInfo.RecentChange(record.sequence(), record.settingId().toString(),
                    setting == null ? "" : setting.nameKey(), record.actorName(), clip(record.oldValue()),
                    clip(record.newValue()), record.timestampMillis(), record.applied(), record.applied() && !record.undone()));
        }
        String version = ModList.get().getModContainerById(WizardsAndBeastsMod.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
        return new AdminSessionInfo(
                viewer.capabilities(),
                version,
                server.isDedicatedServer(),
                server.getPlayerCount(),
                server.getMaxPlayers(),
                server.getAverageTickTimeNanos() / 1_000_000.0f,
                AdminAccess.isRestricted(),
                enabled,
                Module.values().length,
                service.restartPending(),
                recent,
                counts());
    }

    /** Registry sizes and debug leases: cheap reads, taken with every snapshot. */
    private static AdminSessionInfo.Counts counts() {
        int tools = 0;
        List<at.koopro.wizardsandbeasts.admin.debug.DebugLeases.Holding> holdings =
                at.koopro.wizardsandbeasts.admin.debug.DebugLeases.holdings();
        for (at.koopro.wizardsandbeasts.admin.debug.DebugLeases.Holding holding : holdings) {
            tools += holding.tools().size();
        }
        return new AdminSessionInfo.Counts(
                at.koopro.wizardsandbeasts.spell.core.Spells.count(),
                at.koopro.wizardsandbeasts.brew.Brews.all().size(),
                at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService.roster().size(),
                at.koopro.wizardsandbeasts.heritage.Heritage.values().length,
                tools,
                holdings.size());
    }

    /** Values shown on the dashboard are a line each; a long effect list must not swell the snapshot. */
    private static String clip(String value) {
        return value.length() <= 64 ? value : value.substring(0, 63) + "…";
    }
}
