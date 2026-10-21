package at.koopro.wizardsandbeasts.admin.access;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.AdminAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * One NeoForge permission node per {@link AdminCapability}: {@code wizards_and_beasts.admin.config},
 * {@code wizards_and_beasts.admin.players}, {@code wizards_and_beasts.admin.money}, …
 *
 * <p>Every node's default is the mod's existing administrator rule ({@link AdminAccess}: the {@code adminUuids}
 * allow-list when one is configured, operator permission when it is not). So without a permissions mod nothing
 * changes — an administrator holds every capability and nobody else holds any. With one (LuckPerms and the like),
 * a server can give a moderator {@code admin.players} without {@code admin.money}, or a builder {@code admin.visual}
 * alone. The Control Center already reads per-capability authority everywhere (sections a viewer may see but not
 * change carry ⊘, each setting's descriptor says whether this viewer may edit it), so roles need nothing further.
 *
 * <p>The client is never asked. Nodes are resolved on the server for the player the request or command came from.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class AdminPermissions {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<AdminCapability, PermissionNode<Boolean>> NODES = new EnumMap<>(AdminCapability.class);

    static {
        for (AdminCapability capability : AdminCapability.values()) {
            PermissionNode<Boolean> node = new PermissionNode<>(WizardsAndBeastsMod.MODID, capability.node(),
                    PermissionTypes.BOOLEAN, AdminPermissions::byAdminRule);
            node.setInformation(Component.literal("Wizards & Beasts: " + capability.node()),
                    Component.translatable("admin.wizards_and_beasts.capability." + capability.name().toLowerCase(java.util.Locale.ROOT)));
            NODES.put(capability, node);
        }
    }

    private AdminPermissions() {}

    /** The node a capability is granted by. */
    public static PermissionNode<Boolean> node(AdminCapability capability) {
        return NODES.get(capability);
    }

    /** The full node name a permissions mod grants, e.g. {@code wizards_and_beasts.admin.players}. */
    public static String nodeName(AdminCapability capability) {
        return WizardsAndBeastsMod.MODID + "." + capability.node();
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(PermissionNode<?>[]::new));
    }

    /** Whether {@code player} holds {@code capability}, asked of whatever permission handler the server runs. */
    public static boolean has(ServerPlayer player, AdminCapability capability) {
        try {
            return PermissionAPI.getPermission(player, NODES.get(capability));
        } catch (RuntimeException notReady) {
            // Before the permission API has gathered its nodes (or with a handler that rejects ours) fall back to
            // the default the node would have resolved to, rather than failing every admin request.
            LOGGER.debug("[Admin] Permission node {} unavailable, using the administrator rule", nodeName(capability), notReady);
            return byAdminRule(player, player.getUUID());
        }
    }

    /** Every node's default: the mod's administrator rule, unchanged. */
    private static Boolean byAdminRule(@Nullable ServerPlayer player, UUID playerId,
                                       net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext<?>... context) {
        return player != null ? AdminAccess.allows(player.createCommandSourceStack()) : AdminAccess.allows(playerId, false);
    }
}
