package at.koopro.wizardsandbeasts.travel.profile;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionAnchors;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.fitting.BroomFitting;
import at.koopro.wizardsandbeasts.broom.fitting.BroomFittings;
import at.koopro.wizardsandbeasts.broom.fitting.BroomTune;
import at.koopro.wizardsandbeasts.broom.fitting.BroomWear;
import at.koopro.wizardsandbeasts.broom.fitting.BroomWorkshop;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.location.LocationText;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.travel.TravelRecord;
import at.koopro.wizardsandbeasts.travel.courier.CourierParcel;
import at.koopro.wizardsandbeasts.travel.courier.CourierService;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile.Action;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile.ActionKind;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile.Section;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile.Tone;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialService;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialSession;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a player's {@link TravelProfile} from the systems that own each answer. Reads only; every line names a rule
 * the game enforces, and nothing here is anyone else's — the profile goes to its owner alone.
 */
@NullMarked
public final class TravelProfiles {

    private static final String KEY = "gui.wizards_and_beasts.travel_tab.";
    /** Brooms listed from the pack, at most. */
    private static final int MAX_BROOMS = 6;
    /** Floo addresses listed, at most. */
    private static final int MAX_ADDRESSES = 16;

    private TravelProfiles() {}

    public static TravelProfile build(ServerPlayer player) {
        TravelProfile.Builder b = new TravelProfile.Builder();
        brooms(player, b);
        apparition(player, b);
        floo(player, b);
        activities(player, b);
        return b.build();
    }

    // -- brooms ---------------------------------------------------------------------------------------------

    private static void brooms(ServerPlayer player, TravelProfile.Builder b) {
        if (TraceService.isActive()) {
            boolean licensed = MinistryLicences.verdict(player, LicenseType.BROOM).allowed();
            b.add(Section.BROOMS, Component.translatable(KEY + (licensed ? "broom_licence.held" : "broom_licence.none")),
                    Component.translatable(KEY + "broom_licence.detail"), licensed ? Tone.GOOD : Tone.MIXED);
        }
        int listed = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && listed < MAX_BROOMS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BroomItem) {
                broomLine(stack, b);
                listed++;
            }
        }
        if (listed == 0) {
            b.add(Section.BROOMS, Component.translatable(KEY + "no_broom"), Component.translatable(KEY + "no_broom.detail"),
                    Tone.NEUTRAL);
        }
    }

    private static void broomLine(ItemStack stack, TravelProfile.Builder b) {
        BroomDefinition base = BroomItem.resolveDefinition(stack);
        BroomFittings fittings = BroomWorkshop.fittingsOf(stack);
        BroomWear wear = BroomItem.wearOf(stack);
        BroomDefinition flown = BroomTune.apply(base, fittings, wear);
        MutableComponent text = stack.getHoverName().copy().append(Component.literal(" · "))
                .append(base.tier().displayName()).append(Component.literal(" · "))
                .append(Component.translatable(wear.translationKey()));
        MutableComponent detail = Component.translatable(KEY + "broom.stats",
                String.format(Locale.ROOT, "%.0f", flown.maxSpeed() * 20.0f),
                String.format(Locale.ROOT, "%.2f", flown.boostMultiplier()),
                fittings.count(), BroomFitting.capacity(base.tier()));
        for (BroomFitting fitting : fittings.all()) {
            detail.append(Component.literal(" · ")).append(Component.translatable(fitting.translationKey()));
        }
        b.add(Section.BROOMS, text, detail, wear == BroomWear.SOUND ? Tone.NEUTRAL : Tone.BAD);
    }

    // -- apparition -----------------------------------------------------------------------------------------

    private static void apparition(ServerPlayer player, TravelProfile.Builder b) {
        if (!ApparitionServerLogic.hasTraining(player) && !ApparitionServerLogic.isElfMagic(player)) {
            b.add(Section.APPARITION, Component.translatable(KEY + "apparition.untrained"),
                    Component.translatable(KEY + "apparition.untrained.detail"), Tone.NEUTRAL);
            return;
        }
        if (PlayerAbilityHelper.isApparitionLicensed(player)) {
            b.add(Section.APPARITION, Component.translatable(KEY + "apparition.licensed"), Tone.GOOD);
        } else if (!ApparitionServerLogic.isElfMagic(player)) {
            ApparitionLicence.Eligibility verdict = ApparitionLicence.evaluate(player);
            int percent = Math.round(ApparitionLicence.progress(player) * 100.0f);
            Component title = Component.translatable(KEY + "apparition.unlicensed", percent);
            if (!verdict.eligible()) {
                b.add(Section.APPARITION, title, verdict.reason(), Tone.MIXED);
            } else if (ApparitionLicence.examinedAtMinistry()) {
                b.add(Section.APPARITION, title, Component.translatable(KEY + "apparition.test_at_ministry",
                        CurrencyHelper.formatFromKnuts(ApparitionLicence.TEST_FEE_KNUTS)), Tone.MIXED);
            } else {
                b.add(Section.APPARITION, title, Component.translatable(KEY + "apparition.test_ready"), Tone.GOOD,
                        new Action(ActionKind.SIT_TEST, "", Component.translatable(KEY + "action.sit_test")));
            }
        }
        PlayerApparitionPoints points = player.getData(ModAttachments.APPARITION_POINTS.get());
        int room = Math.min(ApparitionAnchors.capacity(player), ApparitionAnchors.ceiling(player));
        b.add(Section.APPARITION, Component.translatable(KEY + "apparition.points", points.points().size(), room),
                Component.translatable(KEY + "apparition.points.detail"), Tone.NEUTRAL,
                new Action(ActionKind.MARK_HERE, "", Component.translatable(KEY + "action.mark_here")));
        for (ApparitionPoint point : points.points()) {
            boolean sameWorld = player.level().dimension().equals(point.dimension());
            Component where = sameWorld
                    ? Component.translatable(KEY + "apparition.distance", Mth.floor(player.position().distanceTo(point.position())))
                    : Component.translatable(KEY + "apparition.other_world");
            b.add(Section.APPARITION, Component.literal(point.name()), where, sameWorld ? Tone.NEUTRAL : Tone.MIXED,
                    new Action(ActionKind.FORGET_POINT, point.name(), Component.translatable(KEY + "action.forget")));
        }
    }

    /** The name the Travel tab memorises a spot under: its coordinates, which a player can rename by command. */
    public static String markName(ServerPlayer player) {
        return player.blockPosition().getX() + " " + player.blockPosition().getZ();
    }

    // -- floo -----------------------------------------------------------------------------------------------

    private static void floo(ServerPlayer player, TravelProfile.Builder b) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(level);
        List<FlooRegistryEntry> known = FlooAccess.visibleTo(player, manager);
        b.add(Section.FLOO, Component.translatable(KEY + "floo.book", known.size()),
                Component.translatable(KEY + "floo.book.detail"), Tone.NEUTRAL);
        int shown = 0;
        for (FlooRegistryEntry entry : known) {
            if (shown++ >= MAX_ADDRESSES) {
                b.add(Section.FLOO, Component.translatable(KEY + "floo.more", known.size() - MAX_ADDRESSES), Tone.NEUTRAL);
                break;
            }
            boolean mine = entry.isOwnedBy(player.getUUID());
            Component detail = entry.tollKnuts() > 0
                    ? Component.translatable(KEY + "floo.toll", CurrencyHelper.formatFromKnuts(entry.tollKnuts()))
                    : Component.translatable(entry.isLandmark() ? KEY + "floo.landmark" : KEY + "floo.free");
            Action action = mine && entry.isPublic()
                    ? new Action(ActionKind.CYCLE_TOLL, entry.networkAddress(), Component.translatable(KEY + "action.toll"))
                    : Action.NONE;
            Tone tone = !entry.isEnabled() ? Tone.BAD : mine ? Tone.GOOD : Tone.NEUTRAL;
            b.add(Section.FLOO, Component.literal(entry.networkAddress()), detail, tone, action);
        }
    }

    // -- activities -----------------------------------------------------------------------------------------

    private static void activities(ServerPlayer player, TravelProfile.Builder b) {
        long now = player.level().getGameTime();
        TravelRecord record = player.getData(ModAttachments.TRAVEL_RECORD.get());
        CourierParcel parcel = CourierService.carried(player);
        if (parcel != null) {
            b.add(Section.ACTIVITIES, Component.translatable(KEY + "courier.carrying", LocationText.name(parcel.destination())),
                    Component.translatable(parcel.express(now) ? KEY + "courier.express" : KEY + "courier.deadline",
                            minutes(parcel.express(now) ? parcel.expressBy() - now : parcel.deadline() - now)),
                    parcel.express(now) ? Tone.GOOD : Tone.MIXED);
        } else {
            b.add(Section.ACTIVITIES, Component.translatable(KEY + "courier.none"),
                    Component.translatable(KEY + "courier.none.detail"), Tone.NEUTRAL);
        }
        TimeTrialSession session = TimeTrialService.session(player);
        if (session != null) {
            b.add(Section.ACTIVITIES, Component.translatable(session.running() ? KEY + "trial.running" : KEY + "trial.armed",
                            session.passes(), session.course().passesToFinish()),
                    Component.translatable(KEY + "trial.par", TimeTrialService.clock(session.course().parTicks())), Tone.GOOD);
        }
        for (Map.Entry<String, Integer> best : record.bestTrialTicks().entrySet()) {
            Identifier course = Identifier.tryParse(best.getKey());
            b.add(Section.ACTIVITIES, Component.translatable(KEY + "trial.best",
                    course == null ? Component.literal(best.getKey()) : LocationText.name(course),
                    TimeTrialService.clock(best.getValue())), Tone.NEUTRAL);
        }
        b.add(Section.ACTIVITIES, Component.translatable(KEY + "tally", record.trialsFinished(), record.parcelsDelivered(),
                record.expressDeliveries(), record.surveyedCells().size(), record.sightedSites().size()), Tone.NEUTRAL);
    }

    private static String minutes(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }
}
