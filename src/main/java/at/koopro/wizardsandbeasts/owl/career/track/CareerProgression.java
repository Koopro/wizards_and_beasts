package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.career.CareerViewS2CPayload;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.CareerProfile;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server side of career tracks: hooks report activity here, the Career tab's buttons arrive here, and
 * every change to {@link PlayerCareerProgress} is made here and nowhere else.
 *
 * <p>Off with {@link Module#OWLS} — careers come from the O.W.L.s — and a no-op for a player whose career has
 * no track. Contract coin needs {@link Module#GRINGOTTS}; without a vault the experience is still paid.
 */
@NullMarked
public final class CareerProgression {

    /** Professional standing one fulfilled contract earns. */
    public static final float CONTRACT_PROFESSION_CREDIT = 2.0f;

    private CareerProgression() {}

    /** The career a player holds, with its track. */
    record Holding(Profession profession, CareerTrack track, String key) {}

    public static boolean available() {
        return ModuleManager.isEnabled(Module.OWLS);
    }

    static @Nullable Holding holding(ServerPlayer player) {
        if (!available()) {
            return null;
        }
        Profession profession = player.getData(ModAttachments.PROFESSION_DATA.get()).profession();
        CareerTrack track = CareerTracks.get(profession);
        if (profession == null || track == null) {
            return null;
        }
        return new Holding(profession, track, key(profession));
    }

    static String key(Profession profession) {
        return profession.name().toLowerCase(Locale.ROOT);
    }

    static long today(ServerPlayer player) {
        return SpellPractice.day(player.level().getGameTime());
    }

    static TrackRecord recordOf(ServerPlayer player, Holding holding) {
        return player.getData(ModAttachments.CAREER_PROGRESS.get()).track(holding.key());
    }

    static void save(ServerPlayer player, Holding holding, TrackRecord record) {
        player.setData(ModAttachments.CAREER_PROGRESS.get(),
                player.getData(ModAttachments.CAREER_PROGRESS.get()).with(holding.key(), record));
    }

    /** The daily board's seed: one per player, so two wizards in the same career see different offers. */
    static long seed(ServerPlayer player) {
        return player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
    }

    // -- activity --------------------------------------------------------------------------------------

    /** A creature's Ministry classification, 0 when unclassified or unknown. */
    public static int ratingOf(@Nullable Identifier species) {
        if (species == null) {
            return 0;
        }
        BestiaryEntry entry = BestiaryEntryRegistry.get(species);
        return entry == null ? 0 : entry.mmRating().orElse(0);
    }

    /** Hook entry point: an activity involving {@code species}. */
    public static void record(ServerPlayer player, ActivityType type, @Nullable Identifier species) {
        record(player, new CareerActivity(type, species, null, ratingOf(species)));
    }

    /** Hook entry point for a bestiary page reaching {@code tier}. */
    public static void recordBestiary(ServerPlayer player, Identifier species, DiscoveryTier tier) {
        record(player, new CareerActivity(ActivityType.BESTIARY_TIER, species, tier, ratingOf(species)));
    }

    /**
     * Counts one activity for the player's career: lifetime counter, experience (within the daily cap),
     * contracts in hand, and any promotion that follows. Silent for players with no track.
     */
    public static void record(ServerPlayer player, CareerActivity done) {
        // The one activity stream: every place keeps its own standing in the same work, whatever the career.
        at.koopro.wizardsandbeasts.location.LocationProgression.record(player, done);
        Holding holding = holding(player);
        if (holding == null) {
            return;
        }
        long day = today(player);
        TrackRecord record = expireOverdue(player, holding, recordOf(player, holding), day);
        record = CareerRules.count(record, done);
        CareerRules.XpAward award = CareerRules.awardXp(holding.track(), record, done, day);
        record = award.record();
        CareerRules.ContractAdvance advance = CareerRules.advanceContracts(holding.track(), record, done);
        record = advance.record();
        for (ContractDefinition finished : advance.finished()) {
            record = fulfil(player, holding, record, finished, day);
        }
        record = promote(player, holding, record);
        save(player, holding, record);
        if (award.xp() > 0) {
            PlayerFeedback.actionBar(player, Component.translatable("career.wizards_and_beasts.xp_gained",
                    award.xp(), Component.translatable(holding.profession().translationKey()))
                    .withStyle(ChatFormatting.GOLD));
        }
        sync(player);
    }

    // -- contracts -------------------------------------------------------------------------------------

    public enum Result { DONE, NO_TRACK, NOT_OFFERED, BOARD_FULL, NOT_HELD, NOT_DELIVERY, NOT_ENOUGH }

    public static Result accept(ServerPlayer player, String contractId) {
        Holding holding = holding(player);
        if (holding == null) {
            return Result.NO_TRACK;
        }
        long day = today(player);
        TrackRecord record = expireOverdue(player, holding, recordOf(player, holding), day);
        ContractDefinition offered = null;
        for (ContractDefinition def : CareerRules.offers(holding.track(), record, seed(player), day)) {
            if (def.id().equals(contractId)) {
                offered = def;
            }
        }
        if (offered == null) {
            save(player, holding, record);
            sync(player);
            return Result.NOT_OFFERED;
        }
        if (record.active().size() >= holding.track().maxActive()) {
            PlayerFeedback.actionBar(player, Component.translatable("career.wizards_and_beasts.board_full",
                    holding.track().maxActive()));
            return Result.BOARD_FULL;
        }
        List<ActiveContract> active = new ArrayList<>(record.active());
        active.add(ActiveContract.taken(offered.id(), day));
        save(player, holding, record.withActive(active));
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable("career.wizards_and_beasts.contract.accepted"),
                CareerText.contractTitle(holding.key(), offered.id()));
        // Treasure work comes with a chart to the site (a Curse-Breaker's surveys).
        at.koopro.wizardsandbeasts.treasure.TreasureCharts.onContractAccepted(player, offered);
        sync(player);
        return Result.DONE;
    }

    public static Result abandon(ServerPlayer player, String contractId) {
        Holding holding = holding(player);
        if (holding == null) {
            return Result.NO_TRACK;
        }
        TrackRecord record = recordOf(player, holding);
        if (record.activeContract(contractId) == null) {
            return Result.NOT_HELD;
        }
        List<ActiveContract> active = new ArrayList<>(record.active());
        active.removeIf(c -> c.id().equals(contractId));
        save(player, holding, record.withActive(active).abandoned(contractId, today(player)));
        sync(player);
        return Result.DONE;
    }

    /**
     * Hands in a delivery contract: the server counts and takes the items itself. Nothing is taken unless
     * all of them are there.
     */
    public static Result turnIn(ServerPlayer player, String contractId) {
        Holding holding = holding(player);
        if (holding == null) {
            return Result.NO_TRACK;
        }
        long day = today(player);
        TrackRecord record = expireOverdue(player, holding, recordOf(player, holding), day);
        ActiveContract held = record.activeContract(contractId);
        ContractDefinition def = holding.track().contract(contractId);
        if (held == null || def == null) {
            save(player, holding, record);
            sync(player);
            return Result.NOT_HELD;
        }
        if (!(def.objective() instanceof ContractDefinition.Delivery delivery)) {
            return Result.NOT_DELIVERY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(delivery.item());
        if (carried(player, item) < delivery.count()) {
            PlayerFeedback.actionBar(player, Component.translatable("career.wizards_and_beasts.contract.missing",
                    delivery.count(), item.getName()));
            return Result.NOT_ENOUGH;
        }
        take(player, item, delivery.count());
        List<ActiveContract> active = new ArrayList<>(record.active());
        active.removeIf(c -> c.id().equals(contractId));
        record = fulfil(player, holding, record.withActive(active), def, day);
        record = promote(player, holding, record);
        save(player, holding, record);
        sync(player);
        return Result.DONE;
    }

    /** How many of {@code item} the player carries. Shared with location boards' deliveries. */
    public static int carried(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code count} of {@code item}; callers check {@link #carried} first. */
    public static void take(ServerPlayer player, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Pays a finished contract: experience, coin into the vault, the completion on record. */
    private static TrackRecord fulfil(ServerPlayer player, Holding holding, TrackRecord record,
                                      ContractDefinition def, long day) {
        ContractDefinition.Reward reward = def.reward();
        boolean paid = reward.knuts() > 0 && ModuleManager.isEnabled(Module.GRINGOTTS)
                && GringottsBilling.credit(player, MoneyFlow.CAREER_CONTRACT, reward.knuts());
        Component body = paid
                ? Component.translatable("career.wizards_and_beasts.contract.reward",
                        CurrencyHelper.formatFromKnuts(reward.knuts()), reward.xp())
                : Component.translatable("career.wizards_and_beasts.contract.reward_xp", reward.xp());
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("career.wizards_and_beasts.contract.fulfilled",
                        CareerText.contractTitle(holding.key(), def.id())), body);
        // Work done well is what a profession respects.
        at.koopro.wizardsandbeasts.standing.StandingService.adjust(player,
                at.koopro.wizardsandbeasts.standing.StandingAxis.PROFESSION, CONTRACT_PROFESSION_CREDIT);
        return record.withXp(record.xp() + reward.xp()).completed(def.id(), day);
    }

    /** Drops contracts past their due day, telling the player which. */
    private static TrackRecord expireOverdue(ServerPlayer player, Holding holding, TrackRecord record, long day) {
        List<ActiveContract> kept = new ArrayList<>(record.active().size());
        for (ActiveContract contract : record.active()) {
            ContractDefinition def = holding.track().contract(contract.id());
            if (def == null) {
                continue; // the contract left the datapack: nothing can complete it any more
            }
            if (CareerRules.expired(def, contract, day)) {
                PlayerFeedback.toast(player, NoticeKind.FAIL,
                        Component.translatable("career.wizards_and_beasts.contract.expired"),
                        CareerText.contractTitle(holding.key(), def.id()));
                continue;
            }
            kept.add(contract);
        }
        return kept.size() == record.active().size() ? record : record.withActive(kept);
    }

    // -- ranks -----------------------------------------------------------------------------------------

    private static TrackRecord promote(ServerPlayer player, Holding holding, TrackRecord record) {
        CareerContext context = new ServerContext(player, record);
        while (CareerRules.canPromote(holding.track(), record, context)) {
            if (!payCertification(player, holding.track().ranks().get(record.rank() + 1))) {
                break;
            }
            record = record.withRank(record.rank() + 1);
            context = new ServerContext(player, record);
            CareerTrack.Rank rank = holding.track().ranks().get(record.rank());
            for (ItemStack reward : rank.rewards()) {
                ItemStack copy = reward.copy();
                at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, copy);
            }
            // A rank that is also a Ministry post (an Auror track's "auror") puts it on the Ministry's record.
            // Never lowers a post the player already holds by appointment.
            rank.ministryRank().ifPresent(post -> at.koopro.wizardsandbeasts.ministry.MinistryRecords.mutate(player,
                    held -> held.rank().atLeast(post) ? held : held.withRank(post)));
            Component title = CareerText.rank(holding.key(), rank.id());
            PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                    Component.translatable("career.wizards_and_beasts.promoted.title"),
                    Component.translatable("career.wizards_and_beasts.promoted.body", title));
            // Public: a promotion is the kind of news the wizarding world talks about.
            if (player.level().getServer() != null) {
                player.level().getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("career.wizards_and_beasts.promoted.broadcast",
                                player.getDisplayName(), title).withStyle(ChatFormatting.GOLD), false);
            }
        }
        return record;
    }

    /**
     * Charges the certification fee a rank asks ({@link CareerGoal.Fee}) as it is reached. Nothing to pay, or
     * Gringotts closed: true. A vault that cannot pay (spent between the check and the promotion): false, and the
     * rank waits for the next activity. Shared with location tiers, which run on this engine.
     */
    public static boolean payCertification(ServerPlayer player, CareerTrack.Rank rank) {
        long fee = CareerGoal.feeOf(rank);
        if (fee <= 0 || !ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return true;
        }
        if (GringottsBilling.charge(player, MoneyFlow.CERTIFICATION, fee)) {
            PlayerFeedback.actionBar(player, Component.translatable("career.wizards_and_beasts.certified",
                    CurrencyHelper.formatFromKnuts(fee)).withStyle(ChatFormatting.GOLD));
            return true;
        }
        return false;
    }

    /** The wage multiplier of the rank held; 1 for a career without a track. */
    public static float wageMultiplier(ServerPlayer player, @Nullable Profession profession) {
        CareerTrack track = CareerTracks.get(profession);
        if (profession == null || track == null || !available()) {
            return 1.0f;
        }
        TrackRecord record = player.getData(ModAttachments.CAREER_PROGRESS.get()).track(key(profession));
        int rank = Math.min(Math.max(record.rank(), 0), track.ranks().size() - 1);
        return track.ranks().get(rank).wageMultiplier();
    }

    /** After a career is chosen: open the record (keeping any rank held before) and show it. */
    public static void onCareerChosen(ServerPlayer player) {
        Holding holding = holding(player);
        if (holding != null) {
            save(player, holding, promote(player, holding, recordOf(player, holding)));
        }
        sync(player);
    }

    // -- view ------------------------------------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        CareerViewS2CPayload.send(player, view(player));
    }

    public static CareerView view(ServerPlayer player) {
        if (!available()) {
            return CareerView.NONE;
        }
        PlayerProfessionData career = player.getData(ModAttachments.PROFESSION_DATA.get());
        Profession profession = career.profession();
        if (profession == null) {
            return CareerView.NONE;
        }
        long baseWage = CareerProfile.of(profession).dailyWage();
        Holding holding = holding(player);
        Component name = Component.translatable(profession.translationKey());
        if (holding == null) {
            return new CareerView(CareerView.FLAT_CAREER, name, Component.empty(), Component.empty(), 0, -1, 0, 0,
                    baseWage, ModuleManager.isEnabled(Module.GRINGOTTS), 0, List.of(), List.of(), List.of());
        }
        long day = today(player);
        TrackRecord record = recordOf(player, holding);
        CareerTrack track = holding.track();
        int rankIndex = Math.min(record.rank(), track.ranks().size() - 1);
        CareerTrack.Rank rank = track.ranks().get(rankIndex);
        CareerTrack.Rank next = rankIndex + 1 < track.ranks().size() ? track.ranks().get(rankIndex + 1) : null;

        List<CareerView.GoalLine> goals = new ArrayList<>();
        if (next != null) {
            CareerContext context = new ServerContext(player, record);
            for (CareerGoal goal : next.requires()) {
                goals.add(new CareerView.GoalLine(goal.describe(), Math.min(goal.progress(context), goal.target()),
                        goal.target()));
            }
        }
        List<CareerView.ContractLine> active = new ArrayList<>();
        for (ActiveContract held : record.active()) {
            ContractDefinition def = track.contract(held.id());
            if (def == null) {
                continue;
            }
            int daysLeft = (int) Math.max(0, CareerRules.dueDay(def, held.acceptedDay()) - day + 1);
            active.add(line(holding, def, progressOf(player, def, held), daysLeft));
        }
        List<CareerView.ContractLine> offers = new ArrayList<>();
        for (ContractDefinition def : CareerRules.offers(track, record, seed(player), day)) {
            offers.add(line(holding, def, 0, def.durationDays()));
        }
        long wage = Math.round(baseWage * (double) rank.wageMultiplier());
        return new CareerView(CareerView.TRACK, name, CareerText.rank(holding.key(), rank.id()),
                next == null ? Component.empty() : CareerText.rank(holding.key(), next.id()),
                record.xp(), next == null ? -1 : next.xp(), rankIndex, track.ranks().size(), wage,
                ModuleManager.isEnabled(Module.GRINGOTTS), track.maxActive(), goals, active, offers);
    }

    private static int progressOf(ServerPlayer player, ContractDefinition def, ActiveContract held) {
        if (def.objective() instanceof ContractDefinition.Delivery delivery) {
            return Math.min(delivery.count(), carried(player, BuiltInRegistries.ITEM.getValue(delivery.item())));
        }
        return held.progress();
    }

    private static CareerView.ContractLine line(Holding holding, ContractDefinition def, int progress, int daysLeft) {
        boolean delivery = def.objective() instanceof ContractDefinition.Delivery;
        int target = def.objective().target();
        return new CareerView.ContractLine(def.id(), CareerText.contractTitle(holding.key(), def.id()),
                CareerText.contractBrief(holding.key(), def.id()), def.objective().describe(),
                progress, target, delivery, daysLeft, def.reward().knuts(), def.reward().xp(),
                delivery && progress >= target);
    }

    /**
     * {@link CareerContext} over a live player's saved state and one track record — a career's, or a location's
     * standing ({@code location.LocationProgression}), which runs on the same engine.
     */
    public record ServerContext(ServerPlayer player, TrackRecord record) implements CareerContext {

        @Override
        public OWLGrade grade(OWLSubject subject) {
            // No examinations, no examination gate: a location rank asking for a grade (the apothecary's trusted
            // supplier wants Potions E) is met while the O.W.L. module is off, as a fee is met with Gringotts closed.
            if (!at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(at.koopro.wizardsandbeasts.module.Module.OWLS)) {
                return OWLGrade.O;
            }
            return player.getData(ModAttachments.OWL_DATA.get()).grades().getOrDefault(subject, OWLGrade.T);
        }

        @Override
        public OWLGrade newtGrade(OWLSubject subject) {
            // Fail-open exactly like grade(): with no examinations there is no N.E.W.T. to ask for.
            if (!at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(at.koopro.wizardsandbeasts.module.Module.OWLS)) {
                return OWLGrade.O;
            }
            return player.getData(ModAttachments.OWL_DATA.get()).newtGrades().getOrDefault(subject, OWLGrade.T);
        }

        @Override
        public int pages(DiscoveryTier minTier, int minRating) {
            PlayerBestiaryData data = player.getData(ModAttachments.BESTIARY_DATA.get());
            int count = 0;
            for (Map.Entry<Identifier, DiscoveryTier> page : data.tiers().entrySet()) {
                if (page.getValue().ordinal() >= minTier.ordinal()
                        && (minRating <= 0 || ratingOf(page.getKey()) >= minRating)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int researchCompleted() {
            int count = 0;
            for (EntryHistory history : player.getData(ModAttachments.BESTIARY_DATA.get()).history().values()) {
                if (history.complete()) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int activityTotal(ActivityFilter filter, boolean distinct) {
            return CareerRules.activityTotal(record.counters(), filter, distinct, CareerProgression::ratingOf);
        }

        @Override
        public int contractsCompleted() {
            return record.contractsCompleted();
        }

        @Override
        public long vaultKnuts() {
            if (!ModuleManager.isEnabled(Module.GRINGOTTS)) {
                return Long.MAX_VALUE;
            }
            return player.getData(ModAttachments.VAULT_DATA.get()).getTotalInKnuts();
        }

        @Override
        public at.koopro.wizardsandbeasts.standing.StandingBand standingBand(
                at.koopro.wizardsandbeasts.standing.StandingAxis axis) {
            return at.koopro.wizardsandbeasts.standing.StandingService.bandOf(player, axis);
        }
    }
}
