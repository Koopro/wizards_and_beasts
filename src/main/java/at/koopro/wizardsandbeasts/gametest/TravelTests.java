package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionWard;
import at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry;
import at.koopro.wizardsandbeasts.broom.fitting.BroomFitting;
import at.koopro.wizardsandbeasts.broom.fitting.BroomFittings;
import at.koopro.wizardsandbeasts.broom.fitting.BroomWear;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.entity.broom.BroomSpeedGuard;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTolls;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.travel.TimeTrialS2CPayload;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import at.koopro.wizardsandbeasts.travel.TravelItems;
import at.koopro.wizardsandbeasts.travel.TravelRecord;
import at.koopro.wizardsandbeasts.travel.courier.CourierParcel;
import at.koopro.wizardsandbeasts.travel.courier.CourierService;
import at.koopro.wizardsandbeasts.travel.profile.TravelActions;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialCourse;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Travel progression against a real server: fittings change the broom the server polices, the workshop never
 * duplicates a fitting, a courier parcel delivers once and only for its courier, a time trial finishes and
 * disqualifies on a teleport, and the Floo honours discovery, tolls and wards.
 *
 * <p>Every scenario uses SURVIVAL players: a creative player keeps the item they use and never pays, which would make
 * "the fitting was taken" and "the toll moved" pass vacuously.
 */
@NullMarked
public final class TravelTests {

    private static final Identifier APOTHECARY = id("apothecary");
    private static final Identifier BROOM_SHOP = id("broom_shop");

    private TravelTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("travel_fitted_broom_is_policed_at_its_fitted_speed",
                "travel: a fitted broom's synced fittings change the cruise speed the speed guard holds it to",
                TravelTests::fittedBroomIsPolicedAtItsFittedSpeed);
        tests.add("travel_workshop_takes_the_fitting_and_the_kit_returns_it",
                "travel: fitting a parked broom takes the item, a full broom refuses and keeps it, the kit hands it back",
                TravelTests::workshopNeverDuplicates);
        tests.add("travel_worn_broom_wanders_more",
                "travel: a battered broom flies with more heading wander than a sound one",
                TravelTests::wornBroomWanders);
        tests.addAlone("travel_courier_parcel_only_for_its_courier",
                "travel: a parcel in another player's hands is refused; the courier delivers once and is paid once",
                TravelTests::courierOnlyForItsCourier);
        tests.add("travel_time_trial_finishes_and_disqualifies_a_teleport",
                "travel: flying the hoops in order finishes and records a best; a jump mid-run disqualifies",
                TravelTests::timeTrialFinishAndDisqualify);
        tests.addAlone("travel_floo_book_lists_found_hearths_and_seeds_old_players",
                "travel: a new player's book lists landmarks only; an existing player keeps every public address once",
                TravelTests::flooDiscovery);
        tests.addAlone("travel_floo_toll_moves_money_once",
                "travel: a toll leaves the traveller and reaches the keeper, now or at their next login; keepers ride free",
                TravelTests::flooTollMovesMoneyOnce);
        tests.addAlone("travel_ward_blocks_floo_arrivals",
                "travel: a ward refuses Floo arrivals unless saved open, and lets admins through when it says so",
                TravelTests::wardBlocksFloo);
        tests.addAlone("travel_tab_actions_touch_only_your_own",
                "travel: a Travel tab toll action on another keeper's hearth changes nothing",
                TravelTests::tabActionsTouchOnlyYourOwn);
    }

    // -- brooms ---------------------------------------------------------------------------------------------

    private static void fittedBroomIsPolicedAtItsFittedSpeed(GameTestHelper helper) {
        clear(helper);
        ItemStack stack = BroomItemRegistry.NIMBUS_2000.get().getDefaultInstance();
        stack.set(ModDataComponents.BROOM_FITTINGS.get(), BroomFittings.NONE.with(BroomFitting.STREAMLINED_TWIGS));
        BroomEntity fitted = helper.spawn(ModEntities.BROOM.get(), new BlockPos(1, 2, 1));
        fitted.setBroomStack(stack);
        BroomEntity plain = helper.spawn(ModEntities.BROOM.get(), new BlockPos(3, 2, 1));
        plain.setBroomStack(BroomItemRegistry.NIMBUS_2000.get().getDefaultInstance());
        ServerPlayer rider = WizardTestSupport.placeMockPlayer(helper, "FittedRider", UUID.randomUUID(), GameType.SURVIVAL);
        try {
            check(helper, fitted.getFittings().in(at.koopro.wizardsandbeasts.broom.fitting.FittingSlot.TWIGS)
                    .orElse(null) == BroomFitting.STREAMLINED_TWIGS, () -> "the entity did not pick up the stack's fittings");
            float base = fitted.baseDefinition().maxSpeed();
            float flown = fitted.getCruiseSpeed();
            check(helper, Math.abs(flown - base * 1.06f) < 1.0e-4f,
                    () -> "a Nimbus with streamlined twigs cruises at " + flown + ", expected " + base * 1.06f);
            float fittedCeiling = BroomSpeedGuard.ceiling(fitted, rider);
            float plainCeiling = BroomSpeedGuard.ceiling(plain, rider);
            check(helper, fittedCeiling > plainCeiling,
                    () -> "the speed guard holds a fitted broom to " + fittedCeiling + " and a plain one to "
                            + plainCeiling + ": it must police the broom actually flown");
            helper.succeed();
        } finally {
            retire(helper, rider);
            fitted.discard();
            plain.discard();
        }
    }

    private static void workshopNeverDuplicates(GameTestHelper helper) {
        clear(helper);
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), new BlockPos(1, 2, 1));
        broom.setBroomStack(BroomItemRegistry.BROOM_ITEM.get().getDefaultInstance());
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Fitter", UUID.randomUUID(), GameType.SURVIVAL);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(BroomItemRegistry.fittingItem(BroomFitting.CUSHIONING_CHARM)));
            broom.interact(player, InteractionHand.MAIN_HAND);
            check(helper, player.getMainHandItem().isEmpty(), () -> "the charm was not taken from the hand");
            check(helper, broom.getFittings().count() == 1, () -> "the broom carries " + broom.getFittings());

            player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(BroomItemRegistry.fittingItem(BroomFitting.BALANCED_TAIL)));
            broom.interact(player, InteractionHand.MAIN_HAND);
            check(helper, player.getMainHandItem().getCount() == 1,
                    () -> "a school broom with no room took the tail anyway");
            check(helper, broom.getFittings().count() == 1, () -> "a school broom took a second fitting");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CanonItemRegistry.BROOMSTICK_SERVICING_KIT.get()));
            broom.interact(player, InteractionHand.MAIN_HAND);
            check(helper, broom.getFittings().isEmpty(), () -> "the kit left " + broom.getFittings());
            int charms = count(player, new ItemStack(BroomItemRegistry.fittingItem(BroomFitting.CUSHIONING_CHARM)));
            check(helper, charms == 1, () -> "the kit returned " + charms + " charms; exactly one went on");
            check(helper, player.getMainHandItem().getDamageValue() == 1, () -> "the kit did not wear");
            helper.succeed();
        } finally {
            retire(helper, player);
            broom.discard();
        }
    }

    private static void wornBroomWanders(GameTestHelper helper) {
        clear(helper);
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), new BlockPos(1, 2, 1));
        broom.setBroomStack(BroomItemRegistry.COMET_260.get().getDefaultInstance());
        try {
            float sound = broom.resolveDefinition().handling().yawDrift();
            broom.setCurrentDurability(5);
            check(helper, broom.getWear() == BroomWear.BATTERED, () -> "5 durability reads as " + broom.getWear());
            float battered = broom.resolveDefinition().handling().yawDrift();
            check(helper, battered > sound, () -> "a battered Comet wanders " + battered + ", a sound one " + sound);
            broom.setCurrentDurability(broom.baseDefinition().durability());
            check(helper, broom.resolveDefinition().handling().yawDrift() == sound,
                    () -> "servicing did not restore the broom's line");
            helper.succeed();
        } finally {
            broom.discard();
        }
    }

    // -- courier --------------------------------------------------------------------------------------------

    private static void courierOnlyForItsCourier(GameTestHelper helper) {
        Runnable gringotts = WizardTestSupport.leaseModule(Module.GRINGOTTS);
        ServerPlayer courier = WizardTestSupport.placeMockPlayer(helper, "Courier", UUID.randomUUID(), GameType.SURVIVAL);
        ServerPlayer thief = WizardTestSupport.placeMockPlayer(helper, "Thief", UUID.randomUUID(), GameType.SURVIVAL);
        try {
            check(helper, Locations.open(APOTHECARY) != null, () -> "the apothecary location did not load");
            Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
            keeper.setNoAi(true);
            keeper.setNoGravity(true);
            keeper.setVillagerData(keeper.getVillagerData().withProfession(helper.getLevel().registryAccess(),
                    ModVillager.APOTHECARY.getKey()));
            long now = helper.getLevel().getGameTime();
            UUID id = UUID.randomUUID();
            CourierParcel parcel = new CourierParcel(id, courier.getUUID(), BROOM_SHOP, APOTHECARY,
                    keeper.blockPosition().offset(300, 0, 0), now, now + CourierService.EXPRESS_TICKS,
                    now + CourierService.DEADLINE_TICKS);
            courier.setData(ModAttachments.TRAVEL_RECORD.get(), TravelRecord.EMPTY.withParcelInFlight(id, parcel.deadline()));

            thief.getInventory().add(parcelStack(parcel));
            check(helper, !CourierService.deliverAt(thief, keeper), () -> "the keeper took a parcel from a stranger");
            check(helper, count(thief, parcelStack(parcel)) == 1, () -> "the refused parcel vanished from the thief");

            courier.getInventory().add(parcelStack(parcel));
            long before = vault(courier).getTotalInKnuts();
            check(helper, CourierService.deliverAt(courier, keeper), () -> "the courier's own parcel was refused");
            TravelRecord after = courier.getData(ModAttachments.TRAVEL_RECORD.get());
            long paid = vault(courier).getTotalInKnuts() - before;
            check(helper, after.parcelsDelivered() == 1 && after.activeParcel().isEmpty(), () -> "record: " + after);
            check(helper, paid == CourierService.postage(300, true), () -> "paid " + paid);

            courier.getInventory().add(parcelStack(parcel));
            check(helper, !CourierService.deliverAt(courier, keeper), () -> "a copy of a delivered parcel paid again");
            check(helper, vault(courier).getTotalInKnuts() - before == paid, () -> "the copy moved money");
            check(helper, count(courier, parcelStack(parcel)) == 0, () -> "the stale copy did not crumble");
            helper.succeed();
        } finally {
            retire(helper, courier);
            retire(helper, thief);
            gringotts.run();
        }
    }

    // -- time trials ----------------------------------------------------------------------------------------

    private static void timeTrialFinishAndDisqualify(GameTestHelper helper) {
        clear(helper);
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), new BlockPos(1, 2, 1));
        broom.setBroomStack(BroomItemRegistry.NIMBUS_2000.get().getDefaultInstance());
        ServerPlayer flyer = WizardTestSupport.placeMockPlayer(helper, "Flyer", UUID.randomUUID(), GameType.SURVIVAL);
        flyer.startRiding(broom);
        Vec3 o = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(0, 4, 0)));
        List<Vec3> rings = List.of(o.add(2, 0, 2), o.add(2, 0, 9), o.add(9, 0, 9), o.add(9, 0, 2));
        TimeTrialCourse course = new TimeTrialCourse(rings, 2.0, TimeTrialCourse.parFor(rings));
        List<Vec3> path = new ArrayList<>();
        path.add(rings.get(0).add(-2, 0, 0));
        for (int i = 0; i <= rings.size(); i++) {
            Vec3 from = i == 0 ? path.get(0) : rings.get((i - 1) % rings.size());
            Vec3 to = rings.get(i % rings.size());
            int steps = (int) Math.ceil(from.distanceTo(to) / 1.0);
            for (int s = 1; s <= steps; s++) {
                path.add(from.lerp(to, s / (double) steps));
            }
        }
        path.add(rings.get(0).add(0, 0, -1));
        TimeTrialService.arm(flyer, BROOM_SHOP.toString(), course);
        for (int t = 0; t < path.size(); t++) {
            Vec3 at = path.get(t);
            helper.runAfterDelay(t + 1, () -> {
                broom.absSnapTo(at.x, at.y, at.z, broom.getYRot(), broom.getXRot());
                TimeTrialService.tick(flyer);
            });
        }
        int finishAt = path.size() + 2;
        helper.runAfterDelay(finishAt, () -> {
            TravelRecord record = flyer.getData(ModAttachments.TRAVEL_RECORD.get());
            check(helper, record.trialsFinished() == 1 && record.bestTicks(BROOM_SHOP.toString()) > 0,
                    () -> "flying the loop did not finish: " + record + ", session " + TimeTrialService.session(flyer));
            check(helper, !TimeTrialService.active(flyer), () -> "the session outlived its finish");

            // A second run, cut short by a jump no broom can fly.
            TimeTrialService.arm(flyer, BROOM_SHOP.toString(), course);
            WizardTestSupport.drainClientboundPayloads(flyer);
            Vec3 a = rings.get(0).add(-1, 0, 0);
            Vec3 b = rings.get(0).add(1, 0, 0);
            broom.absSnapTo(a.x, a.y, a.z, 0f, 0f);
            TimeTrialService.tick(flyer);
            broom.absSnapTo(b.x, b.y, b.z, 0f, 0f);
            TimeTrialService.tick(flyer);
            check(helper, TimeTrialService.session(flyer) != null && TimeTrialService.session(flyer).running(),
                    () -> "the second run never started");
            broom.absSnapTo(b.x + 40, b.y, b.z, 0f, 0f);
            TimeTrialService.tick(flyer);
            check(helper, !TimeTrialService.active(flyer), () -> "a 40-block jump did not end the run");
            boolean told = false;
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(flyer)) {
                if (payload instanceof TimeTrialS2CPayload trial && trial.state() == TimeTrialS2CPayload.ENDED
                        && trial.reasonKey().endsWith("dq_teleported")) {
                    told = true;
                }
            }
            check(helper, told, () -> "the flyer was not told why the run ended");
            check(helper, flyer.getData(ModAttachments.TRAVEL_RECORD.get()).trialsFinished() == 1,
                    () -> "a disqualified run was counted");
            helper.succeed();
        });
        helper.runAfterDelay(finishAt + 1, () -> {
            TimeTrialService.clear(flyer);
            retire(helper, flyer);
            broom.discard();
        });
    }

    // -- floo -----------------------------------------------------------------------------------------------

    private static void flooDiscovery(GameTestHelper helper) {
        FlooNetworkManager manager = FlooNetworkManager.get(helper.getLevel());
        String suffix = Integer.toString(Math.floorMod(helper.getLevel().getGameTime(), 100000));
        String landmark = "Testmark " + suffix;
        String burrow = "Testburrow " + suffix;
        Identifier world = helper.getLevel().dimension().identifier();
        ServerPlayer fresh = WizardTestSupport.placeMockPlayer(helper, "NewWizard", UUID.randomUUID(), GameType.SURVIVAL);
        ServerPlayer veteran = WizardTestSupport.placeMockPlayer(helper, "OldWizard", UUID.randomUUID(), GameType.SURVIVAL);
        try {
            manager.register(landmark, world, helper.absolutePos(new BlockPos(1, 1, 1)), true, Optional.empty());
            manager.register(burrow, world, helper.absolutePos(new BlockPos(3, 1, 1)), true,
                    Optional.of(UUID.randomUUID()));
            FlooRegistryEntry burrowEntry = manager.getEntry(burrow);
            check(helper, burrowEntry != null, () -> "the test hearth did not register");

            fresh.setData(ModAttachments.TRAVEL_RECORD.get(), TravelRecord.EMPTY);
            FlooAccess.seedOnce(fresh, false);
            List<String> freshBook = names(FlooAccess.visibleTo(fresh, manager));
            check(helper, freshBook.contains(landmark), () -> "a landmark is missing from a new book: " + freshBook);
            check(helper, !freshBook.contains(burrow), () -> "a new player already knows someone's hearth");
            check(helper, FlooAccess.mayReach(fresh, burrowEntry), () -> "speaking a public address must still reach it");

            veteran.setData(ModAttachments.TRAVEL_RECORD.get(), TravelRecord.EMPTY);
            FlooAccess.seedOnce(veteran, true);
            check(helper, names(FlooAccess.visibleTo(veteran, manager)).contains(burrow),
                    () -> "an existing player lost a public address they could pick before");
            String later = "Testlater " + suffix;
            manager.register(later, world, helper.absolutePos(new BlockPos(5, 1, 1)), true,
                    Optional.of(UUID.randomUUID()));
            FlooAccess.seedOnce(veteran, true);
            check(helper, !names(FlooAccess.visibleTo(veteran, manager)).contains(later),
                    () -> "seeding ran twice: a hearth registered after it was handed out for free");
            manager.unregister(later);
            helper.succeed();
        } finally {
            manager.unregister(landmark);
            manager.unregister(burrow);
            retire(helper, fresh);
            retire(helper, veteran);
        }
    }

    private static void flooTollMovesMoneyOnce(GameTestHelper helper) {
        Runnable gringotts = WizardTestSupport.leaseModule(Module.GRINGOTTS);
        UUID absentKeeper = UUID.randomUUID();
        ServerPlayer keeper = WizardTestSupport.placeMockPlayer(helper, "Keeper", UUID.randomUUID(), GameType.SURVIVAL);
        ServerPlayer traveller = WizardTestSupport.placeMockPlayer(helper, "Traveller", UUID.randomUUID(), GameType.SURVIVAL);
        ServerPlayer broke = WizardTestSupport.placeMockPlayer(helper, "Broke", UUID.randomUUID(), GameType.SURVIVAL);
        ServerPlayer returning = null;
        try {
            Identifier world = helper.getLevel().dimension().identifier();
            GringottsBilling.credit(traveller, MoneyFlow.ADMIN_DEPOSIT, 1000);
            FlooRegistryEntry kept = new FlooRegistryEntry("Kept", world, BlockPos.ZERO, true, true,
                    Optional.of(keeper.getUUID()), 58);
            long tBefore = vault(traveller).getTotalInKnuts();
            long kBefore = vault(keeper).getTotalInKnuts();
            long toll = FlooTolls.charge(traveller, kept);
            FlooTolls.payKeeper(helper.getLevel(), kept, toll);
            check(helper, toll == 58 && tBefore - vault(traveller).getTotalInKnuts() == 58
                    && vault(keeper).getTotalInKnuts() - kBefore == 58, () -> "the toll did not move 58 Knuts once");
            check(helper, FlooTolls.charge(keeper, kept) == 0, () -> "the keeper paid their own toll");
            long brokeBefore = vault(broke).getTotalInKnuts();
            check(helper, brokeBefore < 58 && FlooTolls.charge(broke, kept) == -1
                    && vault(broke).getTotalInKnuts() == brokeBefore, () -> "a short vault was charged anyway");

            FlooRegistryEntry absent = new FlooRegistryEntry("Absent", world, BlockPos.ZERO, true, true,
                    Optional.of(absentKeeper), 29);
            long paid = FlooTolls.charge(traveller, absent);
            FlooTolls.payKeeper(helper.getLevel(), absent, paid);
            // Logging in is what pays them: placing the player fires PlayerLoggedInEvent (TravelEvents.onLogin).
            returning = WizardTestSupport.placeMockPlayer(helper, "Returning", absentKeeper, GameType.SURVIVAL);
            long collected = vault(returning).getTotalInKnuts();
            check(helper, paid == 29 && collected == 29,
                    () -> "an absent keeper found " + collected + " of " + paid + " in their vault at login");
            FlooTolls.collect(returning);
            check(helper, vault(returning).getTotalInKnuts() == 29, () -> "held tolls paid out twice");
            helper.succeed();
        } finally {
            retire(helper, keeper);
            retire(helper, traveller);
            retire(helper, broke);
            retire(helper, returning);
            gringotts.run();
        }
    }

    private static void wardBlocksFloo(GameTestHelper helper) {
        Identifier world = helper.getLevel().dimension().identifier();
        BlockPos hearth = helper.absolutePos(new BlockPos(2, 1, 2));
        Identifier closed = id("test_ward_closed");
        Identifier open = id("test_ward_open");
        try {
            ApparitionWardRegistry.register(new ApparitionWard(closed, world, new AABB(hearth).inflate(3),
                    Component.literal("closed"), true));
            check(helper, ApparitionWardRegistry.findFlooBlockingWard(helper.getLevel(), hearth, false) != null,
                    () -> "a ward did not stop a Floo arrival");
            check(helper, ApparitionWardRegistry.findFlooBlockingWard(helper.getLevel(), hearth, true) == null,
                    () -> "an admin-permitting ward stopped an admin");
            ApparitionWardRegistry.remove(closed);
            ApparitionWardRegistry.register(new ApparitionWard(open, world, new AABB(hearth).inflate(3),
                    Component.literal("open"), false, false));
            check(helper, ApparitionWardRegistry.findFlooBlockingWard(helper.getLevel(), hearth, false) == null,
                    () -> "a ward saved open to the Floo still stopped it");
            helper.succeed();
        } finally {
            ApparitionWardRegistry.remove(closed);
            ApparitionWardRegistry.remove(open);
        }
    }

    private static void tabActionsTouchOnlyYourOwn(GameTestHelper helper) {
        Runnable floo = WizardTestSupport.leaseModule(Module.FLOO_NETWORK);
        FlooNetworkManager manager = FlooNetworkManager.get(helper.getLevel());
        String address = "Tollhouse " + Math.floorMod(helper.getLevel().getGameTime(), 100000);
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "Owner", UUID.randomUUID(), GameType.SURVIVAL);
        ServerPlayer other = WizardTestSupport.placeMockPlayer(helper, "Other", UUID.randomUUID(), GameType.SURVIVAL);
        try {
            manager.register(address, helper.getLevel().dimension().identifier(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), true, Optional.of(owner.getUUID()));
            TravelActions.perform(other, TravelProfile.ActionKind.CYCLE_TOLL, address);
            check(helper, manager.getEntry(address).tollKnuts() == 0, () -> "a stranger set someone else's toll");
            TravelActions.perform(owner, TravelProfile.ActionKind.CYCLE_TOLL, address);
            check(helper, manager.getEntry(address).tollKnuts() == 29, () -> "the keeper could not set their toll");
            helper.succeed();
        } finally {
            manager.unregister(address);
            retire(helper, owner);
            retire(helper, other);
            floo.run();
        }
    }

    // -- helpers --------------------------------------------------------------------------------------------

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static void clear(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 1; y < 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static ItemStack parcelStack(CourierParcel parcel) {
        ItemStack stack = new ItemStack(TravelItems.COURIER_PARCEL.get());
        stack.set(ModDataComponents.COURIER_PARCEL.get(), parcel);
        return stack;
    }

    private static int count(ServerPlayer player, ItemStack like) {
        int n = 0;
        for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, like)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static List<String> names(List<FlooRegistryEntry> entries) {
        return entries.stream().map(FlooRegistryEntry::networkAddress).toList();
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }
}
