package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Server side of the Heritages section's player payloads. Every handler builds an {@link AdminContext} from the
 * sender first; reads from someone without the players capability are dropped without a reply, writes are
 * answered with a refusal. {@link HeritageAdminService} checks again — this layer only decides what to send back.
 */
@NullMarked
public final class AdminHeritageNetworkService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AdminHeritageNetworkService() {}

    public static boolean sendPlayers(ServerPlayer player) {
        AdminContext actor = AdminContext.of(player);
        if (!HeritageAdminService.authorised(actor)) {
            LOGGER.warn("[Admin] Dropped heritage player list request from unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        PacketDistributor.sendToPlayer(player, new AdminHeritagePayloads.PlayersReply(
                HeritageAdminService.players(actor, player.level().getServer())));
        return true;
    }

    public static boolean sendInspection(ServerPlayer player, UUID target) {
        AdminContext actor = AdminContext.of(player);
        if (!HeritageAdminService.authorised(actor)) {
            LOGGER.warn("[Admin] Dropped heritage inspect request from unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        HeritageAdminService.Inspection inspection = HeritageAdminService.inspect(actor, player.level().getServer(), target);
        if (inspection == null) {
            PacketDistributor.sendToPlayer(player, new AdminHeritagePayloads.ActionReply(false,
                    "admin.wizards_and_beasts.heritage_action.no_player", target.toString()));
            return false;
        }
        PacketDistributor.sendToPlayer(player, new AdminHeritagePayloads.InspectReply(inspection));
        return true;
    }

    public static HeritageAdminService.Outcome assign(ServerPlayer player, AdminHeritagePayloads.AssignRequest request) {
        HeritageAdminService.Outcome outcome = HeritageAdminService.assign(AdminContext.of(player),
                player.level().getServer(), request.player(), request.heritageId(), request.variantId(), request.confirmed());
        reply(player, outcome, request.player());
        return outcome;
    }

    public static HeritageAdminService.Outcome resetOnboarding(ServerPlayer player,
                                                               AdminHeritagePayloads.ResetOnboardingRequest request) {
        HeritageAdminService.Outcome outcome = HeritageAdminService.resetOnboarding(AdminContext.of(player),
                player.level().getServer(), request.player(), request.confirmed());
        reply(player, outcome, request.player());
        return outcome;
    }

    /** The outcome, then — when something changed — the refreshed list and the refreshed player. */
    private static void reply(ServerPlayer player, HeritageAdminService.Outcome outcome, UUID target) {
        PacketDistributor.sendToPlayer(player, new AdminHeritagePayloads.ActionReply(
                outcome.success(), outcome.messageKey(), outcome.detail()));
        if (outcome.success()) {
            sendPlayers(player);
            sendInspection(player, target);
        }
    }
}
