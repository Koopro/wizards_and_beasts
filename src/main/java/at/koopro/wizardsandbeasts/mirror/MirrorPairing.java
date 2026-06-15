package at.koopro.wizardsandbeasts.mirror;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Two-Way-Mirror attunement. Sneak-use an unpaired mirror to attune it; sneak-use a second unpaired
 * mirror within the window to link the pair under a shared id. A single pending slot is enough for the
 * intended flow (one wizard hands a mirror to another, both attune in turn).
 */
public final class MirrorPairing {

    /** Ticks the pending attunement stays open (5 minutes). */
    private static final long PENDING_TTL = 6000L;

    private static Pending pending;

    private MirrorPairing() {}

    private record Pending(UUID pairId, UUID attuner, String attunerName, long expiry) {}

    public static void attune(ServerPlayer player, ItemStack stack, long now) {
        Optional<UUID> existing = stack.getOrDefault(ModDataComponents.MIRROR_PAIR_ID.get(), Optional.empty());
        if (existing.isPresent()) {
            player.displayClientMessage(Component.literal("This mirror is already attuned to its twin.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        boolean pendingValid = pending != null && now < pending.expiry();
        if (pendingValid && !pending.attuner().equals(player.getUUID())) {
            stack.set(ModDataComponents.MIRROR_PAIR_ID.get(), Optional.of(pending.pairId()));
            stack.set(ModDataComponents.MIRROR_RECIPIENT_NAME.get(), Optional.of(pending.attunerName()));
            player.displayClientMessage(Component.literal("The glass clouds, then clears — linked to " + pending.attunerName() + ".")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            pending = null;
            return;
        }

        UUID pairId = UUID.randomUUID();
        stack.set(ModDataComponents.MIRROR_PAIR_ID.get(), Optional.of(pairId));
        pending = new Pending(pairId, player.getUUID(), player.getName().getString(), now + PENDING_TTL);
        player.displayClientMessage(Component.literal("Mirror attuned. Have its twin attuned within 5 minutes.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }
}
