package at.koopro.wizardsandbeasts.admin.access;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who is asking, and what they may do — resolved once per request, on the server.
 *
 * <p>Every call into {@link at.koopro.wizardsandbeasts.admin.AdminSettingService} takes one of these, and
 * the service checks it itself. The command tree and the network handler both build it through
 * {@link #of(CommandSourceStack)}, so a packet cannot arrive with more authority than the same player would
 * have typing the command.
 *
 * @param actorId      the player's UUID, or null for the console / command blocks / RCON
 * @param actorName    display name, for history and logs
 * @param console      true for a non-player source (the recovery path {@code AdminAccess} always admits)
 * @param operator     whether the source holds vanilla game-master permission, independent of the allow-list
 * @param capabilities what this actor may read and change; empty means "not an administrator"
 * @param server       the server the request runs on; null only in unit tests
 */
@NullMarked
public record AdminContext(@Nullable UUID actorId,
                           String actorName,
                           boolean console,
                           boolean operator,
                           Set<AdminCapability> capabilities,
                           @Nullable MinecraftServer server) {

    public AdminContext {
        capabilities = capabilities.isEmpty()
                ? Set.of()
                : java.util.Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    /** The one way server code turns a command source into a context. */
    public static AdminContext of(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return new AdminContext(
                player == null ? null : player.getUUID(),
                source.getTextName(),
                player == null,
                at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions.GAMEMASTER.test(source),
                AdminPolicy.capabilitiesOf(source),
                source.getServer());
    }

    /** A packet's sender, judged exactly as if they had typed the command. */
    public static AdminContext of(ServerPlayer player) {
        return of(player.createCommandSourceStack());
    }

    /** Anyone may read the panel who holds at least one capability. */
    public boolean canRead() {
        return !capabilities.isEmpty();
    }

    public boolean canModify(AdminCapability capability) {
        return capabilities.contains(capability);
    }

    /** Test/system seam: an actor with the given capabilities and no server behind it. */
    public static AdminContext detached(@Nullable UUID actorId, String actorName, Set<AdminCapability> capabilities) {
        return new AdminContext(actorId, actorName, actorId == null, false, capabilities, null);
    }
}
