package at.koopro.wizardsandbeasts.mirror;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.trinket.TwoWayMirrorItem;
import at.koopro.wizardsandbeasts.network.trinket.MirrorCloseS2CPayload;
import at.koopro.wizardsandbeasts.network.trinket.MirrorOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.trinket.MirrorPresenceS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks active Two-Way-Mirror connections. A connection links exactly two online players who hold
 * twin mirrors (same pair id). Mirrors work at any range — sessions persist until a participant
 * disconnects, drops their mirror, or closes the link.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MirrorSessionManager {

    /** How often (ticks) the partner's live look direction is pushed to each viewer. */
    private static final int PRESENCE_INTERVAL = 5;

    /** Bidirectional player→partner links. Both directions are always kept in sync. */
    private static final Map<UUID, UUID> PARTNER = new HashMap<>();

    private MirrorSessionManager() {}

    /** Validates a connection request and, on success, opens the link for both parties. */
    public static void requestConnect(ServerPlayer caller, UUID pairId, String spokenName) {
        MinecraftServer server = caller.level().getServer();
        if (server == null || pairId == null) {
            return;
        }
        if (!holdsPairedMirror(caller, pairId)) {
            caller.displayClientMessage(Component.literal("You are not holding that mirror.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerPlayer target = findHolder(server, pairId, caller.getUUID());
        if (target == null) {
            caller.displayClientMessage(Component.literal("The mirror stays dark. No one answers.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        if (!target.getName().getString().equalsIgnoreCase(spokenName.trim())) {
            caller.displayClientMessage(Component.literal("Nothing stirs — that is not their name.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        open(caller, target);
    }

    private static void open(ServerPlayer a, ServerPlayer b) {
        end(a.getUUID(), a.level().getServer(), null);
        end(b.getUUID(), b.level().getServer(), null);
        PARTNER.put(a.getUUID(), b.getUUID());
        PARTNER.put(b.getUUID(), a.getUUID());
        PacketDistributor.sendToPlayer(a, new MirrorOpenS2CPayload(b.getUUID(), b.getName().getString()));
        PacketDistributor.sendToPlayer(b, new MirrorOpenS2CPayload(a.getUUID(), a.getName().getString()));
    }

    /** Ends the session for {@code playerId} (and its partner). Sends a close note if {@code reason} is set. */
    public static void end(UUID playerId, MinecraftServer server, String reason) {
        UUID partnerId = PARTNER.remove(playerId);
        if (partnerId == null) {
            return;
        }
        PARTNER.remove(partnerId);
        if (reason != null && server != null) {
            notifyClose(server, playerId, reason);
            notifyClose(server, partnerId, reason);
        }
    }

    private static void notifyClose(MinecraftServer server, UUID playerId, String reason) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new MirrorCloseS2CPayload(reason));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PARTNER.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        // Snapshot keys: end() mutates PARTNER. Process each pair once (lower uuid drives).
        for (UUID id : new java.util.ArrayList<>(PARTNER.keySet())) {
            UUID partnerId = PARTNER.get(id);
            if (partnerId == null || id.compareTo(partnerId) > 0) {
                continue;
            }
            ServerPlayer a = server.getPlayerList().getPlayer(id);
            ServerPlayer b = server.getPlayerList().getPlayer(partnerId);
            if (a == null || b == null) {
                end(id, server, "The connection fades.");
                continue;
            }
            UUID pair = sharedPairId(a, b);
            if (pair == null) {
                end(id, server, "The mirror slips from reach.");
                continue;
            }
            if (server.getTickCount() % PRESENCE_INTERVAL == 0) {
                PacketDistributor.sendToPlayer(a, new MirrorPresenceS2CPayload(b.getYRot(), b.getXRot()));
                PacketDistributor.sendToPlayer(b, new MirrorPresenceS2CPayload(a.getYRot(), a.getXRot()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player.getUUID(), player.level().getServer(), "The other glass has gone dark.");
        }
    }

    /** Both still hold a mirror keyed to the same pair id. Returns that id, or null. */
    private static @Nullable UUID sharedPairId(ServerPlayer a, ServerPlayer b) {
        UUID idA = heldPairId(a);
        if (idA != null && idA.equals(heldPairId(b))) {
            return idA;
        }
        return null;
    }

    private static boolean holdsPairedMirror(ServerPlayer player, UUID pairId) {
        return pairId.equals(heldPairId(player, pairId));
    }

    private static @Nullable UUID heldPairId(ServerPlayer player) {
        return heldPairId(player, null);
    }

    /** First mirror pair id found in the player's inventory; if {@code want} is set, only matches it. */
    private static @Nullable UUID heldPairId(ServerPlayer player, UUID want) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!(stack.getItem() instanceof TwoWayMirrorItem)) {
                continue;
            }
            Optional<UUID> id = stack.getOrDefault(ModDataComponents.MIRROR_PAIR_ID.get(), Optional.empty());
            if (id.isPresent() && (want == null || id.get().equals(want))) {
                return id.get();
            }
        }
        return null;
    }

    private static @Nullable ServerPlayer findHolder(MinecraftServer server, UUID pairId, UUID exclude) {
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.getUUID().equals(exclude)) {
                continue;
            }
            if (pairId.equals(heldPairId(candidate, pairId))) {
                return candidate;
            }
        }
        return null;
    }
}
