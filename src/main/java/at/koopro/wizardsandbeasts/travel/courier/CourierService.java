package at.koopro.wizardsandbeasts.travel.courier;

import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.location.LocationText;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.travel.TravelItems;
import at.koopro.wizardsandbeasts.travel.TravelRecord;
import at.koopro.wizardsandbeasts.util.InventoryHandover;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Courier work: a keeper hands over a sealed parcel for another place, and the player carries it there in time.
 *
 * <p>How it is carried is the player's business — broom, Floo, Apparition, on foot — and that is the point: a parcel
 * is the reason to have a fast broom, a known Floo address or an Apparition point near the place it is going. The
 * time limits are set so a school broom makes the deadline comfortably and only a good broom (or a good route)
 * makes the express window.
 *
 * <p>Two halves, both server-side. {@link Dispatch} is a location service ({@code courier_dispatch}) — the board's
 * usual tier gate and presence check already ran. {@link #deliverAt} runs when the player sneak-uses a host, ahead of
 * the board opening. The finish records {@link ActivityType#COURIER_DELIVERED} (and {@link ActivityType#COURIER_EXPRESS})
 * through the one activity stream, so standing and contracts count it without a new contract kind.
 *
 * <p><b>One parcel in flight.</b> The parcel's id is written into the courier's {@link TravelRecord}; only that id
 * delivers, and only by them. A second parcel cannot be taken while one is in flight, a parcel stashed in a chest
 * cannot be cashed later alongside a new one, and a parcel handed to someone else is just paper in their hands.
 */
@NullMarked
public final class CourierService {

    /** Blocks between the dispatching counter and the receiving one, below which a hand-over is not a delivery. */
    public static final double MIN_DISTANCE = 96.0;
    /** Ticks from dispatch to lapse: one Minecraft day. */
    public static final long DEADLINE_TICKS = 24_000L;
    /** Ticks from dispatch within which delivery is express: eight minutes. */
    public static final long EXPRESS_TICKS = 9_600L;
    /** Postage per block of distance between the two counters. */
    public static final double KNUTS_PER_BLOCK = 0.25;
    /** Least and most a delivery pays before the express bonus. */
    public static final long MIN_POSTAGE = 20L;
    public static final long MAX_POSTAGE = 240L;
    /** Express multiplier on postage. */
    public static final double EXPRESS_BONUS = 1.5;

    private CourierService() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("courier_dispatch", new Dispatch());
    }

    /**
     * Postage for a delivery {@code distance} blocks from the dispatching counter. Pure, for the tests and the
     * Travel tab.
     */
    public static long postage(double distance, boolean express) {
        long base = Math.max(MIN_POSTAGE, Math.min(MAX_POSTAGE, Math.round(distance * KNUTS_PER_BLOCK)));
        return express ? Math.round(base * EXPRESS_BONUS) : base;
    }

    /**
     * Places a parcel from {@code origin} can be addressed to: every open location kept by a host (a parcel is handed
     * to a keeper), other than {@code origin} itself.
     */
    public static List<Identifier> destinations(Identifier origin) {
        List<Identifier> out = new ArrayList<>();
        for (Map.Entry<Identifier, LocationDefinition> entry : Locations.all().entrySet()) {
            if (entry.getKey().equals(origin) || Locations.open(entry.getKey()) == null
                    || entry.getValue().presence().hosts().isEmpty()) {
                continue;
            }
            out.add(entry.getKey());
        }
        out.sort(Identifier::compareTo);
        return out;
    }

    private static TravelRecord record(ServerPlayer player) {
        return player.getData(ModAttachments.TRAVEL_RECORD.get());
    }

    private static void save(ServerPlayer player, TravelRecord record) {
        player.setData(ModAttachments.TRAVEL_RECORD.get(), record);
    }

    /** The {@code courier_dispatch} service: hands over one parcel addressed to another place. */
    static final class Dispatch implements LocationService {
        @Override
        public Quote quote(ServerPlayer player, Use use) {
            if (record(player).hasParcelInFlight(player.level().getGameTime())) {
                return Quote.no(Component.translatable("travel.wizards_and_beasts.courier.already"));
            }
            if (destinations(use.location()).isEmpty()) {
                return Quote.no(Component.translatable("travel.wizards_and_beasts.courier.nowhere"));
            }
            return Quote.yes(Component.translatable("travel.wizards_and_beasts.courier.quote",
                    CurrencyHelper.formatFromKnuts(MIN_POSTAGE), CurrencyHelper.formatFromKnuts(
                            Math.round(MAX_POSTAGE * EXPRESS_BONUS))));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            long now = player.level().getGameTime();
            TravelRecord record = record(player);
            if (record.hasParcelInFlight(now)) {
                PlayerFeedback.refuse(player, LocationText.service(id()),
                        Component.translatable("travel.wizards_and_beasts.courier.already"));
                return false;
            }
            List<Identifier> places = destinations(use.location());
            if (places.isEmpty()) {
                PlayerFeedback.refuse(player, LocationText.service(id()),
                        Component.translatable("travel.wizards_and_beasts.courier.nowhere"));
                return false;
            }
            if (!use.charge(player, use.fee())) {
                return false;
            }
            Identifier destination = places.get(player.getRandom().nextInt(places.size()));
            CourierParcel parcel = new CourierParcel(UUID.randomUUID(), player.getUUID(), use.location(), destination,
                    player.blockPosition(), now, now + EXPRESS_TICKS, now + DEADLINE_TICKS);
            ItemStack stack = new ItemStack(TravelItems.COURIER_PARCEL.get());
            stack.set(ModDataComponents.COURIER_PARCEL.get(), parcel);
            save(player, record.withParcelInFlight(parcel.id(), parcel.deadline()));
            InventoryHandover.giveOrDrop(player, stack);
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("travel.wizards_and_beasts.courier.dispatched"),
                    Component.translatable("travel.wizards_and_beasts.courier.dispatched.body",
                            LocationText.name(destination), (int) MIN_DISTANCE));
            return true;
        }

        private static Identifier id() {
            return Identifier.fromNamespaceAndPath("wizards_and_beasts", "courier_dispatch");
        }
    }

    /**
     * Hands every deliverable parcel the player carries to {@code host}, if it keeps the place the parcel is
     * addressed to. Called on a sneak-use of a host, before its board opens; never cancels the board.
     *
     * @return whether anything was delivered
     */
    public static boolean deliverAt(ServerPlayer player, Entity host) {
        Identifier location = LocationPresence.hostedBy(host);
        if (location == null || Locations.open(location) == null) {
            return false;
        }
        boolean delivered = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            CourierParcel parcel = stack.get(ModDataComponents.COURIER_PARCEL.get());
            if (parcel == null || !parcel.destination().equals(location)) {
                continue;
            }
            if (deliver(player, host, stack, parcel)) {
                delivered = true;
            }
        }
        return delivered;
    }

    /** Why a parcel was not accepted; {@link #DELIVERED} when it was. */
    public enum Outcome { DELIVERED, NOT_YOURS, NOT_IN_FLIGHT, LAPSED, TOO_CLOSE }

    /**
     * The acceptance rule on its own, for the tests: every check the counter makes, in order, given what the
     * server knows.
     */
    public static Outcome judge(CourierParcel parcel, UUID deliverer, TravelRecord record, long now,
                                double distanceFromOrigin) {
        if (!parcel.courier().equals(deliverer)) {
            return Outcome.NOT_YOURS;
        }
        if (record.activeParcel().filter(parcel.id()::equals).isEmpty()) {
            return Outcome.NOT_IN_FLIGHT;
        }
        if (parcel.lapsed(now)) {
            return Outcome.LAPSED;
        }
        if (distanceFromOrigin < MIN_DISTANCE) {
            return Outcome.TOO_CLOSE;
        }
        return Outcome.DELIVERED;
    }

    private static boolean deliver(ServerPlayer player, Entity host, ItemStack stack, CourierParcel parcel) {
        long now = player.level().getGameTime();
        TravelRecord record = record(player);
        double distance = Math.sqrt(host.blockPosition().distSqr(parcel.originPos()));
        Outcome outcome = judge(parcel, player.getUUID(), record, now, distance);
        if (outcome != Outcome.DELIVERED) {
            if (outcome == Outcome.LAPSED || outcome == Outcome.NOT_IN_FLIGHT) {
                stack.shrink(1);
            }
            PlayerFeedback.refuse(player, Component.translatable("item.wizards_and_beasts.courier_parcel"),
                    Component.translatable("travel.wizards_and_beasts.courier.refused."
                            + outcome.name().toLowerCase(java.util.Locale.ROOT)));
            return false;
        }
        boolean express = parcel.express(now);
        long pay = postage(distance, express);
        stack.shrink(1);
        save(player, record.withParcelDelivered(express));
        boolean paid = ModuleManager.isEnabled(Module.GRINGOTTS)
                && GringottsBilling.credit(player, MoneyFlow.COURIER_PAY, pay);
        CareerProgression.record(player, ActivityType.COURIER_DELIVERED, parcel.origin());
        if (express) {
            CareerProgression.record(player, ActivityType.COURIER_EXPRESS, parcel.origin());
        }
        player.level().playSound(null, host.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL,
                0.8f, 1.1f);
        Component body = paid
                ? Component.translatable("travel.wizards_and_beasts.courier.paid", CurrencyHelper.formatFromKnuts(pay))
                : Component.translatable("travel.wizards_and_beasts.courier.thanks");
        PlayerFeedback.toast(player, NoticeKind.SUCCESS, Component.translatable(express
                ? "travel.wizards_and_beasts.courier.delivered_express"
                : "travel.wizards_and_beasts.courier.delivered"), body);
        return true;
    }

    /**
     * Lets a parcel go when it lapses in an inventory, and frees the courier's slot. Called from the parcel item's
     * inventory tick, which only runs for the stack's holder.
     */
    public static void lapse(ServerPlayer holder, ItemStack stack, CourierParcel parcel) {
        stack.shrink(1);
        TravelRecord record = record(holder);
        if (record.activeParcel().filter(parcel.id()::equals).isPresent()) {
            save(holder, record.withoutParcel());
        }
        PlayerFeedback.actionBar(holder, Component.translatable("travel.wizards_and_beasts.courier.lapsed",
                LocationText.name(parcel.destination())).withStyle(ChatFormatting.GRAY));
    }

    /** The parcel the player carries that is in flight, for the Travel tab; null when none is on them. */
    public static @Nullable CourierParcel carried(ServerPlayer player) {
        TravelRecord record = record(player);
        if (record.activeParcel().isEmpty()) {
            return null;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            CourierParcel parcel = inventory.getItem(slot).get(ModDataComponents.COURIER_PARCEL.get());
            if (parcel != null && parcel.id().equals(record.activeParcel().get())) {
                return parcel;
            }
        }
        return null;
    }
}
