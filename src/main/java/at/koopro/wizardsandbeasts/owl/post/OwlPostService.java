package at.koopro.wizardsandbeasts.owl.post;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The owl-post delivery loop: hand a stack to an owl, wait, receive it.
 *
 * <h2>What this is, and what the module was</h2>
 * <p>{@code Module.OWLS} is the O.W.L. <em>examination</em> system — grades, subjects, professions,
 * the examination desk. There was never an owl in it, and no post, no delivery and no bird existed
 * anywhere in the mod. {@code KNOWN_ISSUES.md} claimed "owl post delivers, but routing edge cases
 * are unverified", which described code that did not exist.
 *
 * <p>This is the delivery half, built new and kept deliberately small: a scheduled hand-off with
 * honest failure states, not an entity with a flight path. Owls as creatures are a separate piece of
 * work and this does not pretend to be it.
 *
 * <h2>Failure states, all of them spoken</h2>
 * <ul>
 *   <li>module off — refused at send, nothing is taken;</li>
 *   <li>empty hand — refused at send;</li>
 *   <li>sending to yourself — refused at send, because it is always a mistake and the parcel would
 *       come straight back;</li>
 *   <li>recipient offline at arrival — the parcel waits in the air rather than vanishing, and is
 *       retried;</li>
 *   <li>recipient's inventory full — the owl turns around and flies it back to the sender;</li>
 *   <li>sender's inventory also full on return — the parcel drops at the sender's feet if they are
 *       online, and otherwise keeps waiting.</li>
 * </ul>
 *
 * <p>Nothing is ever silently destroyed. A parcel exists in exactly one place at a time: the stack
 * leaves the sender's hand at send, lives in {@link OwlPostData} while in flight, and is only removed
 * from the queue once it is in someone's inventory or on the ground.
 */
@NullMarked
public final class OwlPostService {

    /**
     * How long an owl takes, in ticks. Two minutes.
     *
     * <p>Long enough that the post is not a teleporter and a player plans around it, short enough
     * that a test — or a player trying it once — does not have to wait out a Minecraft day.
     */
    public static final long FLIGHT_TICKS = 2400L;

    /** How often the queue is examined. A parcel is never more than a second late. */
    public static final int SWEEP_INTERVAL_TICKS = 20;

    private OwlPostService() {}

    // -- sending ---------------------------------------------------------------------------------

    /** Why a send was refused, or that it was accepted. */
    public enum SendResult {
        SENT,
        MODULE_DISABLED,
        NOTHING_TO_SEND,
        SELF_ADDRESSED;

        public boolean ok() {
            return this == SENT;
        }

        /** The line a player is shown. Translated, because every one of these is player-facing. */
        public Component message() {
            return switch (this) {
                case SENT -> Component.translatable("owl.wizards_and_beasts.sent");
                case MODULE_DISABLED -> Component.translatable("owl.wizards_and_beasts.fail.module");
                case NOTHING_TO_SEND -> Component.translatable("owl.wizards_and_beasts.fail.empty");
                case SELF_ADDRESSED -> Component.translatable("owl.wizards_and_beasts.fail.self");
            };
        }
    }

    /**
     * Send the stack in {@code sender}'s main hand to {@code recipientId}.
     *
     * <p>The stack is taken from the hand only once the send is certain to be accepted, so a refused
     * send never costs the player anything.
     */
    public static SendResult send(ServerPlayer sender, UUID recipientId) {
        if (!ModuleManager.isEnabled(Module.OWLS)) {
            return SendResult.MODULE_DISABLED;
        }
        if (sender.getUUID().equals(recipientId)) {
            return SendResult.SELF_ADDRESSED;
        }
        ItemStack held = sender.getMainHandItem();
        if (held.isEmpty()) {
            return SendResult.NOTHING_TO_SEND;
        }

        ServerLevel overworld = sender.level().getServer().overworld();
        long now = overworld.getGameTime();
        ItemStack payload = held.copy();
        sender.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        OwlPostData.get(overworld).add(new OwlParcel(
                UUID.randomUUID(), sender.getUUID(), recipientId, payload, now + FLIGHT_TICKS, false));

        sender.level().playSound(null, sender.blockPosition(),
                SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6f, 1.4f);
        return SendResult.SENT;
    }

    // -- delivery --------------------------------------------------------------------------------

    /**
     * Advance the queue. Called from the server tick handler once a second.
     *
     * <p>Iterates a snapshot: delivery mutates the queue, and a parcel that turns around is
     * re-added, so walking the live list would visit it twice in one sweep.
     */
    public static void sweep(ServerLevel overworld) {
        if (!ModuleManager.isEnabled(Module.OWLS)) {
            // Parcels are left in the queue rather than dropped: switching the module back on must
            // not have cost anyone their post.
            return;
        }
        OwlPostData data = OwlPostData.get(overworld);
        long now = overworld.getGameTime();
        for (OwlParcel parcel : data.parcels()) {
            if (parcel.hasArrived(now)) {
                attemptDelivery(overworld, data, parcel, now);
            }
        }
    }

    private static void attemptDelivery(ServerLevel overworld, OwlPostData data,
                                        OwlParcel parcel, long now) {
        ServerPlayer target = overworld.getServer().getPlayerList().getPlayer(parcel.currentTarget());
        if (target == null) {
            // Offline. The owl circles: nothing is lost, and the parcel is tried again next sweep.
            return;
        }

        ItemStack payload = parcel.payload().copy();
        boolean accepted = target.getInventory().add(payload);

        if (accepted) {
            data.remove(parcel.id());
            target.level().playSound(null, target.blockPosition(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.7f, 1.1f);
            PlayerFeedback.toast(target,
                    at.koopro.wizardsandbeasts.feedback.NoticeKind.SUCCESS,
                    Component.translatable(parcel.returning()
                            ? "owl.wizards_and_beasts.returned.title"
                            : "owl.wizards_and_beasts.delivered.title"),
                    parcel.payload().getHoverName());
            return;
        }

        if (!parcel.returning()) {
            // Inventory full. Turn the owl around rather than dropping the stack at the feet of
            // someone who did not ask for it, and tell the sender why when it lands.
            data.replace(parcel.turnedAround(now, FLIGHT_TICKS));
            PlayerFeedback.actionBar(target, Component.translatable("owl.wizards_and_beasts.fail.full_recipient"));
            return;
        }

        // Already returning and the sender is full too. Drop it at their feet: they are online and
        // looking at it, which is the one case where dropping is better than another lap.
        data.remove(parcel.id());
        target.drop(payload, false);
        PlayerFeedback.actionBar(target, Component.translatable("owl.wizards_and_beasts.fail.full_sender"));
    }

    // -- reporting -------------------------------------------------------------------------------

    /** A one-line status for {@code player}, for the command and for any UI that wants it. */
    public static Component status(ServerLevel overworld, ServerPlayer player) {
        OwlPostData data = OwlPostData.get(overworld);
        int inbound = data.inboundFor(player.getUUID()).size();
        int outbound = data.sentBy(player.getUUID()).size();
        return Component.translatable("owl.wizards_and_beasts.status", inbound, outbound);
    }

    /** Ticks until the next parcel addressed to {@code player} lands, or {@code null} if none. */
    public static @Nullable Long ticksUntilNextArrival(ServerLevel overworld, ServerPlayer player) {
        long now = overworld.getGameTime();
        return OwlPostData.get(overworld).inboundFor(player.getUUID()).stream()
                .mapToLong(parcel -> Math.max(0L, parcel.arrivalTick() - now))
                .min()
                .stream().boxed().findFirst().orElse(null);
    }
}
