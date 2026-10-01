package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.admin.creature.CreatureTestSpawns;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Server side of the Creatures section's payloads. Reads from someone who is not an administrator with the content
 * capability are dropped without a reply; the world actions are answered, and {@link CreatureTestSpawns} checks the
 * world capability itself.
 */
@NullMarked
public final class AdminCreatureNetworkService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AdminCreatureNetworkService() {}

    public static boolean sendList(ServerPlayer player) {
        if (!authorised(player, "creature list")) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new AdminCreaturePayloads.ListReply(
                CreatureAdminService.list(player.level().getServer())));
        return true;
    }

    public static boolean sendDetail(ServerPlayer player, String creatureId) {
        if (!authorised(player, "creature detail")) {
            return false;
        }
        CreatureAdminService.Detail detail = CreatureAdminService.detail(player.level().getServer(), creatureId);
        if (detail == null) {
            PacketDistributor.sendToPlayer(player, new AdminCreaturePayloads.ActionReply(false,
                    "admin.wizards_and_beasts.creature_action.unknown_creature", creatureId));
            return false;
        }
        PacketDistributor.sendToPlayer(player, new AdminCreaturePayloads.DetailReply(detail));
        return true;
    }

    public static CreatureTestSpawns.Outcome spawn(ServerPlayer player, AdminCreaturePayloads.SpawnRequest request) {
        CreatureTestSpawns.Outcome outcome = CreatureTestSpawns.spawn(player, request.creatureId(),
                request.variant(), request.noAi());
        reply(player, outcome);
        return outcome;
    }

    public static CreatureTestSpawns.Outcome cleanup(ServerPlayer player) {
        CreatureTestSpawns.Outcome outcome = CreatureTestSpawns.cleanup(player);
        reply(player, outcome);
        return outcome;
    }

    private static void reply(ServerPlayer player, CreatureTestSpawns.Outcome outcome) {
        PacketDistributor.sendToPlayer(player, new AdminCreaturePayloads.ActionReply(
                outcome.success(), outcome.messageKey(), outcome.detail()));
    }

    private static boolean authorised(ServerPlayer player, String what) {
        if (CreatureAdminService.authorised(AdminContext.of(player))) {
            return true;
        }
        LOGGER.warn("[Admin] Dropped {} request from unauthorised {} ({})", what, player.getName().getString(), player.getUUID());
        return false;
    }
}
