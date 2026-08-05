package at.koopro.wizardsandbeasts.apparition.sidealong;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Consensual side-along: one wizard offers to take another, and the other has a hundred ticks to say yes.
 *
 * <p>The hostile flavour needs none of this — seizing someone mid-Disapparition is the whole point of it, and
 * it stays exactly where it was, a proximity grab evaluated at release. This is the other half: an invitation
 * that expires, so nobody is dragged across a continent by a stranger who clicked them once an hour ago.
 *
 * <p>Both the offer and the acceptance are transient and per-player, cleaned up on logout.
 */
@NullMarked
public final class SideAlongService {

    /** How long an offer stands. */
    public static final int OFFER_TICKS = 100;

    /** Offers awaiting an answer, keyed by the player being asked. */
    private static final PlayerScopedState<Offer> OFFERS = PlayerScopedState.create("apparition_sidealong_offer");
    /** Accepted partners, keyed by the caster. Cleared when the jump resolves or the partner logs out. */
    private static final PlayerScopedState<UUID> PARTNERS = PlayerScopedState.create("apparition_sidealong_partner");

    private SideAlongService() {}

    /** One standing offer. {@code expiresAtTick} is measured against the invitee's own tick count. */
    private record Offer(UUID casterId, int expiresAtTick) {}

    /** Offers to take {@code invitee} along. Replaces any offer they were already sitting on. */
    public static void offer(ServerPlayer caster, ServerPlayer invitee) {
        OFFERS.put(invitee, new Offer(caster.getUUID(), invitee.tickCount + OFFER_TICKS));
        invitee.displayClientMessage(Component.translatable(
                        "apparition.wizards_and_beasts.sidealong.offered", caster.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        caster.displayClientMessage(Component.translatable(
                        "apparition.wizards_and_beasts.sidealong.offer_sent", invitee.getDisplayName())
                .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Takes {@code invitee} up on a standing offer.
     *
     * @return false when there was no offer, or it had already lapsed
     */
    public static boolean accept(ServerPlayer invitee) {
        Offer offer = OFFERS.get(invitee);
        if (offer == null || invitee.tickCount > offer.expiresAtTick()) {
            OFFERS.remove(invitee);
            invitee.displayClientMessage(Component.translatable(
                            "apparition.wizards_and_beasts.sidealong.no_offer")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        OFFERS.remove(invitee);
        PARTNERS.put(offer.casterId(), invitee.getUUID());
        invitee.displayClientMessage(Component.translatable(
                        "apparition.wizards_and_beasts.sidealong.accepted")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** Drops a lapsed offer. Called from the invitee's own tick. */
    public static void tick(ServerPlayer player) {
        Offer offer = OFFERS.get(player);
        if (offer != null && player.tickCount > offer.expiresAtTick()) {
            OFFERS.remove(player);
            player.displayClientMessage(Component.translatable(
                            "apparition.wizards_and_beasts.sidealong.lapsed")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** The player who agreed to travel with {@code caster}, if any is still online and nearby. */
    public static @Nullable ServerPlayer partnerOf(ServerPlayer caster) {
        UUID partnerId = PARTNERS.get(caster.getUUID());
        if (partnerId == null) {
            return null;
        }
        return caster.level().getServer() == null
                ? null
                : caster.level().getServer().getPlayerList().getPlayer(partnerId);
    }

    /** Consumes the agreement. A side-along is a single journey, not a standing arrangement. */
    public static void clearPartner(ServerPlayer caster) {
        PARTNERS.remove(caster.getUUID());
    }
}
