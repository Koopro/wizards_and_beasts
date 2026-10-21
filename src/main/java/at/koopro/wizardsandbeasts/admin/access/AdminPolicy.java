package at.koopro.wizardsandbeasts.admin.access;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Maps a command source to the {@link AdminCapability capabilities} it holds.
 *
 * <p>A player holds each capability whose permission node ({@link AdminPermissions}) the server's permission
 * handler grants them. Every node defaults to the mod's administrator rule, so on a server without a permissions
 * mod this is the original all-or-nothing answer; with one, capabilities split into roles. The console, command
 * blocks and RCON hold everything: they are the recovery path when a permission setup locks every player out.
 *
 * <p>This is the only place authority is decided. Settings, panels, payload handlers and commands all ask an
 * {@link AdminContext} built from here, and nothing on the client is consulted.
 */
@NullMarked
public final class AdminPolicy {

    private AdminPolicy() {}

    public static Set<AdminCapability> capabilitiesOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return EnumSet.allOf(AdminCapability.class);
        }
        EnumSet<AdminCapability> held = EnumSet.noneOf(AdminCapability.class);
        for (AdminCapability capability : AdminCapability.values()) {
            if (AdminPermissions.has(player, capability)) {
                held.add(capability);
            }
        }
        return held;
    }

    /** Whether {@code source} holds any administrative capability: the gate on {@code /wandb admin}. */
    public static boolean isAdministrator(CommandSourceStack source) {
        return !capabilitiesOf(source).isEmpty();
    }

    /** A command requirement for one capability, for subtrees that do not go through a checking service. */
    public static Predicate<CommandSourceStack> requires(AdminCapability capability) {
        return source -> capabilitiesOf(source).contains(capability);
    }

    /** The original mapping from the single administrator verdict to a capability set. Pure, for tests. */
    public static Set<AdminCapability> forVerdict(boolean isAdmin) {
        return isAdmin ? EnumSet.allOf(AdminCapability.class) : EnumSet.noneOf(AdminCapability.class);
    }
}
