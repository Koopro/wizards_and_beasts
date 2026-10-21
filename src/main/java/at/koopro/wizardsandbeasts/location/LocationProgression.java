package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceGate;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.location.LocationViewS2CPayload;
import at.koopro.wizardsandbeasts.owl.career.track.CareerActivity;
import at.koopro.wizardsandbeasts.owl.career.track.CareerContext;
import at.koopro.wizardsandbeasts.owl.career.track.CareerGoal;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.owl.career.track.CareerRules;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The server side of every location: activity reported anywhere lands here, the board's buttons arrive here, and
 * every change to {@link PlayerLocationStanding} is made here and nowhere else.
 *
 * <p>The arithmetic is the career engine's ({@link CareerRules}: reputation and its daily caps, contract progress,
 * the deterministic daily board, tier-ups). This class only adds what a <em>place</em> needs on top: the module gate,
 * being there ({@link LocationPresence}) to take or hand in work and to buy a service, and services.
 */
@NullMarked
public final class LocationProgression {

    private LocationProgression() {}

    /** Outcome of a board action, for callers and tests. */
    public enum Result { DONE, UNKNOWN, NOT_THERE, NOT_OFFERED, BOARD_FULL, NOT_HELD, NOT_DELIVERY, NOT_ENOUGH,
        TIER_TOO_LOW, REFUSED }

    static String key(Identifier location) {
        return location.toString();
    }

    static long today(ServerPlayer player) {
        return SpellPractice.day(player.level().getGameTime());
    }

    /** A per-player, per-place seed, so two customers of one bank see different boards. */
    static long seed(ServerPlayer player, Identifier location) {
        return player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits()
                ^ ((long) location.hashCode() << 17);
    }

    public static TrackRecord recordOf(ServerPlayer player, Identifier location) {
        return player.getData(ModAttachments.LOCATION_STANDING.get()).at(key(location));
    }

    static void save(ServerPlayer player, Identifier location, TrackRecord record) {
        player.setData(ModAttachments.LOCATION_STANDING.get(),
                player.getData(ModAttachments.LOCATION_STANDING.get()).with(key(location), record));
    }

    /** The tier index held at {@code location}; 0 when never dealt with. */
    public static int tier(ServerPlayer player, Identifier location) {
        LocationDefinition def = Locations.get(location);
        if (def == null) {
            return 0;
        }
        return Math.min(Math.max(recordOf(player, location).rank(), 0), def.standing().ranks().size() - 1);
    }

    // -- activity --------------------------------------------------------------------------------------

    /**
     * Counts one activity at every open location whose standing reads its type: lifetime counter, reputation
     * (within the daily cap), contracts in hand, tier-ups. Wherever it happened — a curse broken in a tomb is
     * still news at the bank. Called from {@link CareerProgression#record}, the hooks' one entry point.
     */
    public static void record(ServerPlayer player, CareerActivity done) {
        for (Map.Entry<Identifier, LocationDefinition> entry : Locations.all().entrySet()) {
            LocationDefinition def = Locations.open(entry.getKey());
            if (def == null || !def.activityTypes().contains(done.type())) {
                continue;
            }
            record(player, entry.getKey(), def, done);
        }
    }

    private static void record(ServerPlayer player, Identifier location, LocationDefinition def, CareerActivity done) {
        CareerTrack standing = def.standing();
        long day = today(player);
        TrackRecord record = expireOverdue(player, location, def, recordOf(player, location), day);
        record = CareerRules.count(record, done);
        CareerRules.XpAward award = CareerRules.awardXp(standing, record, done, day);
        record = award.record();
        CareerRules.ContractAdvance advance = CareerRules.advanceContracts(standing, record, done);
        record = advance.record();
        for (ContractDefinition finished : advance.finished()) {
            record = fulfil(player, location, record, finished, day);
        }
        record = promote(player, location, def, record);
        save(player, location, record);
        if (award.xp() > 0) {
            PlayerFeedback.actionBar(player, Component.translatable("location.wizards_and_beasts.rep_gained",
                    award.xp(), LocationText.name(location)).withStyle(ChatFormatting.GOLD));
        }
    }

    // -- hosts -----------------------------------------------------------------------------------------

    /**
     * A player turned to an NPC that keeps a location: its papers are checked, the player is at its counter, and
     * the board opens. Returns whether the host handled it (false when it keeps no open location).
     */
    public static boolean openAtHost(ServerPlayer player, Entity host) {
        Identifier location = LocationPresence.hostedBy(host);
        if (location == null) {
            return false;
        }
        LocationDefinition def = Locations.open(location);
        if (def == null) {
            return false;
        }
        if (def.papers().isPresent() && !MinistryLicenceGate.admit(player, def.papers().get())) {
            return true;
        }
        LocationPresence.admit(player, host);
        sync(player, location, true);
        return true;
    }

    // -- contracts -------------------------------------------------------------------------------------

    public static Result accept(ServerPlayer player, Identifier location, String contractId) {
        LocationDefinition def = Locations.open(location);
        if (def == null) {
            return Result.UNKNOWN;
        }
        if (!LocationPresence.isAt(player, def)) {
            notThere(player, location);
            return Result.NOT_THERE;
        }
        long day = today(player);
        TrackRecord record = expireOverdue(player, location, def, recordOf(player, location), day);
        ContractDefinition offered = null;
        for (ContractDefinition candidate : CareerRules.offers(def.standing(), record, seed(player, location), day)) {
            if (candidate.id().equals(contractId)) {
                offered = candidate;
            }
        }
        if (offered == null) {
            save(player, location, record);
            sync(player, location, false);
            return Result.NOT_OFFERED;
        }
        if (record.active().size() >= def.standing().maxActive()) {
            PlayerFeedback.actionBar(player, Component.translatable("career.wizards_and_beasts.board_full",
                    def.standing().maxActive()));
            return Result.BOARD_FULL;
        }
        List<ActiveContract> active = new ArrayList<>(record.active());
        active.add(ActiveContract.taken(offered.id(), day));
        save(player, location, record.withActive(active));
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable("career.wizards_and_beasts.contract.accepted"),
                LocationText.contractTitle(location, offered.id()));
        // Work that names a treasure site comes with a chart to it.
        TreasureCharts.onContractAccepted(player, offered);
        sync(player, location, false);
        return Result.DONE;
    }

    public static Result abandon(ServerPlayer player, Identifier location, String contractId) {
        if (Locations.open(location) == null) {
            return Result.UNKNOWN;
        }
        TrackRecord record = recordOf(player, location);
        if (record.activeContract(contractId) == null) {
            return Result.NOT_HELD;
        }
        List<ActiveContract> active = new ArrayList<>(record.active());
        active.removeIf(c -> c.id().equals(contractId));
        save(player, location, record.withActive(active).abandoned(contractId, today(player)));
        sync(player, location, false);
        return Result.DONE;
    }

    /** Hands in a delivery at the counter: the server counts and takes the items; nothing unless all are there. */
    public static Result turnIn(ServerPlayer player, Identifier location, String contractId) {
        LocationDefinition def = Locations.open(location);
        if (def == null) {
            return Result.UNKNOWN;
        }
        if (!LocationPresence.isAt(player, def)) {
            notThere(player, location);
            return Result.NOT_THERE;
        }
        long day = today(player);
        TrackRecord record = expireOverdue(player, location, def, recordOf(player, location), day);
        ActiveContract held = record.activeContract(contractId);
        ContractDefinition contract = def.standing().contract(contractId);
        if (held == null || contract == null) {
            save(player, location, record);
            sync(player, location, false);
            return Result.NOT_HELD;
        }
        if (!(contract.objective() instanceof ContractDefinition.Delivery delivery)) {
            return Result.NOT_DELIVERY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(delivery.item());
        if (CareerProgression.carried(player, item) < delivery.count()) {
            PlayerFeedback.actionBar(player, Component.translatable("career.wizards_and_beasts.contract.missing",
                    delivery.count(), item.getName()));
            return Result.NOT_ENOUGH;
        }
        // The reward must be payable before the goods are taken: a refused credit must not leave the goods gone.
        if (contract.reward().knuts() > 0 && ModuleManager.isEnabled(Module.GRINGOTTS)
                && !player.getData(ModAttachments.VAULT_DATA.get()).canCredit(contract.reward().knuts())) {
            PlayerFeedback.actionBar(player, Component.translatable("gringotts.wizards_and_beasts.reject.full")
                    .withStyle(ChatFormatting.RED));
            return Result.REFUSED;
        }
        CareerProgression.take(player, item, delivery.count());
        // A delivery to a fence is smuggling: the goods' own class decides what the Ministry would make of it.
        at.koopro.wizardsandbeasts.market.IllicitDealing.roll(player, def,
                at.koopro.wizardsandbeasts.market.MarketGoods.classify(new ItemStack(item)), delivery.count());
        List<ActiveContract> active = new ArrayList<>(record.active());
        active.removeIf(c -> c.id().equals(contractId));
        record = fulfil(player, location, record.withActive(active), contract, day);
        record = promote(player, location, def, record);
        save(player, location, record);
        sync(player, location, false);
        return Result.DONE;
    }

    /** Pays a finished contract: reputation, coin into the vault, the completion on record. */
    private static TrackRecord fulfil(ServerPlayer player, Identifier location, TrackRecord record,
                                      ContractDefinition contract, long day) {
        ContractDefinition.Reward reward = contract.reward();
        LocationDefinition def = Locations.get(location);
        MoneyFlow flow = def != null && def.risk().isPresent() ? MoneyFlow.ILLICIT_SALE : MoneyFlow.LOCATION_CONTRACT;
        boolean paid = reward.knuts() > 0 && ModuleManager.isEnabled(Module.GRINGOTTS)
                && GringottsBilling.credit(player, flow, reward.knuts());
        Component body = paid
                ? Component.translatable("location.wizards_and_beasts.contract.reward",
                        CurrencyHelper.formatFromKnuts(reward.knuts()), reward.xp())
                : Component.translatable("location.wizards_and_beasts.contract.reward_rep", reward.xp());
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("career.wizards_and_beasts.contract.fulfilled",
                        LocationText.contractTitle(location, contract.id())), body);
        return record.withXp(record.xp() + reward.xp()).completed(contract.id(), day);
    }

    private static TrackRecord expireOverdue(ServerPlayer player, Identifier location, LocationDefinition def,
                                             TrackRecord record, long day) {
        List<ActiveContract> kept = new ArrayList<>(record.active().size());
        for (ActiveContract held : record.active()) {
            ContractDefinition contract = def.standing().contract(held.id());
            if (contract == null) {
                continue; // left the datapack: nothing can complete it any more
            }
            if (CareerRules.expired(contract, held, day)) {
                PlayerFeedback.toast(player, NoticeKind.FAIL,
                        Component.translatable("career.wizards_and_beasts.contract.expired"),
                        LocationText.contractTitle(location, contract.id()));
                continue;
            }
            kept.add(held);
        }
        return kept.size() == record.active().size() ? record : record.withActive(kept);
    }

    private static TrackRecord promote(ServerPlayer player, Identifier location, LocationDefinition def,
                                       TrackRecord record) {
        CareerTrack standing = def.standing();
        CareerContext context = new CareerProgression.ServerContext(player, record);
        while (CareerRules.canPromote(standing, record, context)) {
            if (!CareerProgression.payCertification(player, standing.ranks().get(record.rank() + 1))) {
                break;
            }
            record = record.withRank(record.rank() + 1);
            context = new CareerProgression.ServerContext(player, record);
            CareerTrack.Rank tier = standing.ranks().get(record.rank());
            for (ItemStack reward : tier.rewards()) {
                ItemStack copy = reward.copy();
                at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, copy);
            }
            PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                    Component.translatable("location.wizards_and_beasts.tier_up.title", LocationText.name(location)),
                    Component.translatable("location.wizards_and_beasts.tier_up.body",
                            LocationText.tier(location, tier.id())));
        }
        return record;
    }

    // -- services --------------------------------------------------------------------------------------

    public static Result useService(ServerPlayer player, Identifier location, Identifier serviceId) {
        LocationDefinition def = Locations.open(location);
        ServiceOffer offer = def == null ? null : def.service(serviceId);
        LocationService service = LocationServices.get(serviceId);
        if (def == null || offer == null || service == null) {
            return Result.UNKNOWN;
        }
        if (!LocationPresence.isAt(player, def)) {
            notThere(player, location);
            return Result.NOT_THERE;
        }
        int tiersAbove = tier(player, location) - def.standing().rankIndex(offer.minTier());
        if (tiersAbove < 0) {
            PlayerFeedback.refuse(player, LocationText.service(serviceId),
                    Component.translatable("location.wizards_and_beasts.tier_required",
                            LocationText.tier(location, offer.minTier())));
            return Result.TIER_TOO_LOW;
        }
        boolean done = service.perform(player, new LocationService.Use(location, def, offer, tiersAbove));
        sync(player, location, false);
        return done ? Result.DONE : Result.REFUSED;
    }

    private static void notThere(ServerPlayer player, Identifier location) {
        PlayerFeedback.actionBar(player, Component.translatable("location.wizards_and_beasts.not_there",
                LocationText.name(location)).withStyle(ChatFormatting.RED));
    }

    // -- view ------------------------------------------------------------------------------------------

    /** Sends the board; {@code open} asks the client to show it, otherwise an open board just refreshes. */
    public static void sync(ServerPlayer player, Identifier location, boolean open) {
        LocationView view = view(player, location);
        if (view != null) {
            LocationViewS2CPayload.send(player, view, open);
        }
    }

    /**
     * The player's standing at every open place, for the Character Sheet: tier, reputation and the next tier, with
     * none of a board's contracts, services or prices. Places whose module is off are left out, as their boards are.
     * Sorted by id so the list does not reshuffle between refreshes.
     */
    public static List<PlaceStanding> standings(ServerPlayer player) {
        List<Identifier> ids = new ArrayList<>(Locations.all().keySet());
        ids.sort(java.util.Comparator.comparing(Identifier::toString));
        List<PlaceStanding> out = new ArrayList<>();
        for (Identifier location : ids) {
            LocationDefinition def = Locations.open(location);
            if (def == null || def.standing().ranks().isEmpty() || out.size() >= PlaceStanding.MAX_PLACES) {
                continue;
            }
            CareerTrack standing = def.standing();
            int tierIndex = tier(player, location);
            CareerTrack.Rank tier = standing.ranks().get(tierIndex);
            CareerTrack.Rank next = tierIndex + 1 < standing.ranks().size() ? standing.ranks().get(tierIndex + 1) : null;
            out.add(new PlaceStanding(location, LocationText.name(location), LocationText.tier(location, tier.id()),
                    next == null ? Component.empty() : LocationText.tier(location, next.id()),
                    recordOf(player, location).xp(), next == null ? -1 : next.xp(), tierIndex, standing.ranks().size(),
                    def.risk().isPresent()));
        }
        return out;
    }

    public static @Nullable LocationView view(ServerPlayer player, Identifier location) {
        LocationDefinition def = Locations.open(location);
        if (def == null) {
            return null;
        }
        long day = today(player);
        TrackRecord record = recordOf(player, location);
        CareerTrack standing = def.standing();
        int tierIndex = tier(player, location);
        CareerTrack.Rank tier = standing.ranks().get(tierIndex);
        CareerTrack.Rank next = tierIndex + 1 < standing.ranks().size() ? standing.ranks().get(tierIndex + 1) : null;

        List<CareerView.GoalLine> goals = new ArrayList<>();
        if (next != null) {
            CareerContext context = new CareerProgression.ServerContext(player, record);
            for (CareerGoal goal : next.requires()) {
                goals.add(new CareerView.GoalLine(goal.describe(), Math.min(goal.progress(context), goal.target()),
                        goal.target()));
            }
        }
        List<LocationView.ServiceLine> services = new ArrayList<>();
        for (ServiceOffer offer : def.services()) {
            LocationService service = LocationServices.get(offer.service());
            int above = tierIndex - standing.rankIndex(offer.minTier());
            if (service == null) {
                continue;
            }
            if (above < 0) {
                services.add(new LocationView.ServiceLine(offer.service(), LocationText.service(offer.service()),
                        Component.translatable("location.wizards_and_beasts.tier_required",
                                LocationText.tier(location, offer.minTier())), false, false));
                continue;
            }
            LocationService.Quote quote = service.quote(player, new LocationService.Use(location, def, offer, above));
            services.add(new LocationView.ServiceLine(offer.service(), LocationText.service(offer.service()),
                    quote.detail(), true, quote.available()));
        }
        List<CareerView.ContractLine> active = new ArrayList<>();
        for (ActiveContract held : record.active()) {
            ContractDefinition contract = standing.contract(held.id());
            if (contract == null) {
                continue;
            }
            int daysLeft = (int) Math.max(0, CareerRules.dueDay(contract, held.acceptedDay()) - day + 1);
            active.add(line(location, contract, progressOf(player, contract, held), daysLeft));
        }
        List<CareerView.ContractLine> offers = new ArrayList<>();
        for (ContractDefinition contract : CareerRules.offers(standing, record, seed(player, location), day)) {
            offers.add(line(location, contract, 0, contract.durationDays()));
        }
        return new LocationView(location, LocationText.name(location), def.skin().orElse(""),
                LocationPresence.isAt(player, def),
                LocationText.tier(location, tier.id()),
                next == null ? Component.empty() : LocationText.tier(location, next.id()),
                record.xp(), next == null ? -1 : next.xp(), tierIndex, standing.ranks().size(), standing.maxActive(),
                goals, services, active, offers,
                at.koopro.wizardsandbeasts.market.MarketTrade.lines(player, location, def), def.risk().isPresent());
    }

    private static int progressOf(ServerPlayer player, ContractDefinition contract, ActiveContract held) {
        if (contract.objective() instanceof ContractDefinition.Delivery delivery) {
            return Math.min(delivery.count(),
                    CareerProgression.carried(player, BuiltInRegistries.ITEM.getValue(delivery.item())));
        }
        return held.progress();
    }

    private static CareerView.ContractLine line(Identifier location, ContractDefinition contract,
                                                int progress, int daysLeft) {
        boolean delivery = contract.objective() instanceof ContractDefinition.Delivery;
        int target = contract.objective().target();
        return new CareerView.ContractLine(contract.id(), LocationText.contractTitle(location, contract.id()),
                LocationText.contractBrief(location, contract.id()), contract.objective().describe(),
                progress, target, delivery, daysLeft, contract.reward().knuts(), contract.reward().xp(),
                delivery && progress >= target);
    }
}
