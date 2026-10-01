package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.broom.BroomSettingProvider;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.admin.wand.WandSettingProvider;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.broom.rules.BroomRules;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolEntry;
import at.koopro.wizardsandbeasts.wand.rules.WandRules;
import at.koopro.wizardsandbeasts.wand.rules.WandRulesService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Wands and Travel sections' server boundary against the live wand registries, wandmaking recipes, broom
 * definitions and the real bench/Ollivander/broom paths. Every change is restored through the same service.
 */
public final class AdminWandBroomTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    private static final String NS = "wizards_and_beasts";
    private static final Identifier HOLLY = Identifier.fromNamespaceAndPath(NS, "holly");
    private static final Identifier PHOENIX = Identifier.fromNamespaceAndPath(NS, "phoenix_feather");
    private static final Identifier UNICORN = Identifier.fromNamespaceAndPath(NS, "unicorn_hair");
    /** A shipped core no wandmaking recipe uses. */
    private static final Identifier UNPAIRED_CORE = Identifier.fromNamespaceAndPath(NS, "rougarou_hair");
    private static final Identifier NIMBUS = Identifier.fromNamespaceAndPath(NS, "nimbus_2000");
    /** The school broom: no tier licence, so only the module and the withdrawal can refuse it. */
    private static final Identifier SCHOOL_BROOM = Identifier.fromNamespaceAndPath(NS, "broom");
    private static final BlockPos BROOM_AT = new BlockPos(1, 2, 1);

    private AdminWandBroomTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_wand_invalid_combinations_rejected",
                "admin wands: unknown woods and cores, pairings no recipe allows, withdrawn pairings, bad lengths, flexibilities and looks are refused; a valid make resolves its stats",
                AdminWandBroomTests::invalidCombinationsRejected);
        tests.add("admin_wand_withdrawal_reaches_ollivander_and_keeps_one_pair",
                "admin wands: a withdrawn wood is never offered by Ollivander, and the last makeable pairing cannot be withdrawn",
                AdminWandBroomTests::withdrawalReachesOllivander);
        tests.add("admin_wand_unauthorised_rejected",
                "admin wands and brooms: a non-admin can neither withdraw a wood, change a broom, read the pages nor take a test wand",
                AdminWandBroomTests::unauthorisedRejected);
        tests.add("admin_wand_test_wand_is_unbonded",
                "admin wands: a test wand is the recipe's own make with the requested look, masterless like a fresh bench wand",
                AdminWandBroomTests::testWandIsUnbonded);
        tests.add("admin_broom_values_reach_the_synced_definitions",
                "admin brooms: a stat override and the server speed scale land in the synced definitions; a withdrawn broom is not deployed",
                AdminWandBroomTests::broomValuesReachDefinitions);
        tests.add("admin_broom_speed_guard_sets_down_overspeeder",
                "admin brooms: a rider whose broom keeps moving faster than the server's definition allows is set down",
                AdminWandBroomTests::speedGuardSetsDownOverspeeder);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static WandAdminService.Request make(Identifier wood, Identifier core, float length, String flex, String preset) {
        return new WandAdminService.Request(wood.toString(), core.toString(), length, flex, preset);
    }

    private static ServerPlayer admin(GameTestHelper helper, String name, boolean[] opped) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID listed = AdminFrameworkTests.firstAllowListedUuid();
        ServerPlayer admin = listed == null
                ? WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL)
                : WizardTestSupport.placeMockPlayer(helper, name, listed, GameType.SURVIVAL);
        if (listed == null) {
            server.getPlayerList().op(admin.nameAndId());
            opped[0] = true;
        }
        return admin;
    }

    // ── wands ──

    private static void invalidCombinationsRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        record Case(WandAdminService.Request request, String reason) {}
        Identifier nowhere = Identifier.fromNamespaceAndPath(NS, "no_such_part");
        List<Case> cases = List.of(
                new Case(make(nowhere, PHOENIX, 11f, "SUPPLE", ""), "unknown_wood"),
                new Case(make(HOLLY, nowhere, 11f, "SUPPLE", ""), "unknown_core"),
                new Case(new WandAdminService.Request("not an id", PHOENIX.toString(), 11f, "SUPPLE", ""), "unknown_wood"),
                new Case(make(HOLLY, UNPAIRED_CORE, 11f, "SUPPLE", ""), "no_recipe"),
                new Case(make(HOLLY, PHOENIX, 30f, "SUPPLE", ""), "bad_length"),
                new Case(make(HOLLY, PHOENIX, Float.NaN, "SUPPLE", ""), "bad_length"),
                new Case(make(HOLLY, PHOENIX, 11f, "WOBBLY", ""), "bad_flexibility"),
                new Case(make(HOLLY, PHOENIX, 11f, "SUPPLE", "wizards_and_beasts:no_such_look"), "bad_preset"));
        for (Case c : cases) {
            String problem = WandAdminService.problem(server, c.request());
            check(helper, c.reason().equals(problem), () -> c.request() + " → " + problem + ", expected " + c.reason());
            AdminWandPayloads.Preview preview = WandAdminService.preview(server, c.request());
            check(helper, !preview.valid() && preview.facts().isEmpty(), () -> "an invalid make previewed as valid: " + c.request());
        }

        // The catalog shows each part's canonical lore verbatim from its datapack file.
        WandAdminService.Catalog catalog = WandAdminService.catalog(server, CONSOLE);
        AdminWandPayloads.PartInfo holly = catalog.woods().stream().filter(p -> p.id().equals(HOLLY.toString()))
                .findFirst().orElse(null);
        check(helper, holly != null && !holly.lore().isEmpty() && !holly.facts().isEmpty(),
                () -> "holly's page is missing its lore or facts: " + holly);
        check(helper, catalog.pairs().contains(WandRules.pairKey(HOLLY, PHOENIX))
                        && !catalog.pairs().contains(WandRules.pairKey(HOLLY, UNPAIRED_CORE)),
                () -> "the catalog's pairings are not the recipes'");

        AdminWandPayloads.Preview valid = WandAdminService.preview(server, make(HOLLY, PHOENIX, 11f, "SUPPLE", "wizards_and_beasts:harry"));
        check(helper, valid.valid() && !valid.facts().isEmpty(), () -> "holly + phoenix feather was refused: " + valid);

        Identifier pair = WandSettingProvider.pairId(HOLLY, PHOENIX);
        AdminResult withdraw = service().change(CONSOLE, pair, "false", true);
        try {
            check(helper, withdraw.applied() && !WandRules.mayMake(HOLLY, PHOENIX), () -> "withdrawing the pairing failed: " + withdraw);
            String problem = WandAdminService.problem(server, make(HOLLY, PHOENIX, 11f, "SUPPLE", ""));
            check(helper, "withdrawn".equals(problem), () -> "a withdrawn pairing previewed as " + problem);
            check(helper, WandRulesService.makeable(server, HOLLY, PHOENIX).isEmpty(), () -> "a withdrawn pairing is still makeable");
            check(helper, WandRulesService.recipeFor(server, HOLLY, PHOENIX).isPresent(), () -> "withdrawing deleted the recipe itself");
        } finally {
            service().reset(CONSOLE, pair, true);
        }
        check(helper, WandRules.mayMake(HOLLY, PHOENIX), () -> "reset did not restore the pairing");
        AdminResult unpaired = service().change(CONSOLE,
                WandSettingProvider.pairId(HOLLY, UNPAIRED_CORE), "false", true);
        check(helper, unpaired.rejection() == AdminRejection.UNKNOWN_SETTING,
                () -> "a pairing no recipe defines became a setting: " + unpaired);
        helper.succeed();
    }

    private static void withdrawalReachesOllivander(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer customer = WizardTestSupport.placeMockPlayer(helper, "WandAdminCustomer", GameType.SURVIVAL);
        Identifier holly = WandSettingProvider.woodId(HOLLY);
        List<Identifier> woods = server.registryAccess()
                .lookupOrThrow(at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries.WAND_WOOD_REGISTRY)
                .keySet().stream().sorted().toList();
        try {
            customer.experienceLevel = 100;
            AdminResult off = service().change(CONSOLE, holly, "false", true);
            check(helper, off.applied() && !WandRules.woodEnabled(HOLLY), () -> "withdrawing holly failed: " + off);
            for (int i = 0; i < 40; i++) {
                for (OllivanderPoolEntry offered : OllivanderTrialMenu.pickTrials(customer)) {
                    check(helper, !offered.woodKey().equals(HOLLY), () -> "Ollivander offered withdrawn holly: " + offered);
                }
            }

            // Withdraw every wood but the last; the last one's withdrawal must be refused as a conflict.
            for (int i = 0; i < woods.size() - 1; i++) {
                Identifier id = WandSettingProvider.woodId(woods.get(i));
                AdminResult each = service().change(CONSOLE, id, "false", true);
                check(helper, each.applied() || !WandRules.woodEnabled(woods.get(i)), () -> "withdrawing " + id + " failed: " + each);
            }
            Identifier last = WandSettingProvider.woodId(woods.getLast());
            AdminResult refused = service().change(CONSOLE, last, "false", true);
            check(helper, refused.rejection() == AdminRejection.CONFLICT && WandRules.woodEnabled(woods.getLast()),
                    () -> "the last makeable wood could be withdrawn: " + refused);
            helper.succeed();
        } finally {
            for (Identifier wood : woods) {
                service().reset(CONSOLE, WandSettingProvider.woodId(wood), true);
            }
            WizardTestSupport.retire(helper, customer);
        }
    }

    private static void unauthorisedRejected(GameTestHelper helper) {
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "WandAdminOutsider", GameType.SURVIVAL);
        try {
            AdminResult wood = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(1,
                    WandSettingProvider.woodId(HOLLY), "false", true));
            check(helper, wood.rejection() == AdminRejection.UNAUTHORIZED && WandRules.woodEnabled(HOLLY),
                    () -> "a non-admin withdrew a wood: " + wood);
            AdminResult speed = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(2,
                    BroomSettingProvider.id(NIMBUS, "max_speed"), "2.0", true));
            check(helper, speed.rejection() == AdminRejection.UNAUTHORIZED && BroomRules.overridesFor(NIMBUS.toString()).isEmpty(),
                    () -> "a non-admin changed a broom's speed: " + speed);
            AdminResult scale = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(3,
                    Identifier.fromNamespaceAndPath(NS, "broom_server_speed_scale"), "2.0", true));
            check(helper, scale.rejection() == AdminRejection.UNAUTHORIZED, () -> "a non-admin changed the speed scale: " + scale);

            WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, !AdminWandPayloads.sendCatalog(outsider) && !AdminBroomPayloads.sendList(outsider),
                    () -> "a non-admin was sent the wand or broom pages");
            WandAdminService.Outcome given = WandAdminService.giveTestWand(outsider, make(HOLLY, PHOENIX, 11f, "SUPPLE", ""));
            check(helper, !given.success(), () -> "a non-admin was given a test wand");
            check(helper, outsider.getInventory().getNonEquipmentItems().stream().noneMatch(s -> s.getItem() instanceof WandItem),
                    () -> "a wand reached a non-admin's inventory");
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(outsider)) {
                check(helper, !(payload instanceof AdminWandPayloads.CatalogReply) && !(payload instanceof AdminBroomPayloads.ListReply),
                        () -> "a non-admin received " + payload.type().id());
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
        }
    }

    private static void testWandIsUnbonded(GameTestHelper helper) {
        boolean[] opped = {false};
        ServerPlayer wandmaker = admin(helper, "WandAdminMaker", opped);
        MinecraftServer server = helper.getLevel().getServer();
        try {
            // A fresh player may be handed a starter wand on join; only the wand given here counts.
            wandmaker.getInventory().clearContent();
            WandAdminService.Outcome refused = WandAdminService.giveTestWand(wandmaker, make(HOLLY, UNPAIRED_CORE, 11f, "SUPPLE", ""));
            check(helper, !refused.success(), () -> "a pairing no recipe allows was handed out");

            WandAdminService.Outcome given = WandAdminService.giveTestWand(wandmaker,
                    make(HOLLY, UNICORN, 10.5f, "PLIANT", "wizards_and_beasts:harry"));
            check(helper, given.success(), () -> "a valid test wand was refused: " + given.messageKey());
            ItemStack wand = wandmaker.getInventory().getNonEquipmentItems().stream().filter(s -> s.getItem() instanceof WandItem)
                    .findFirst().orElse(ItemStack.EMPTY);
            check(helper, !wand.isEmpty(), () -> "no wand arrived");
            check(helper, HOLLY.equals(wand.get(WandComponents.WAND_WOOD.get()))
                            && UNICORN.equals(wand.get(WandComponents.WAND_CORE.get())),
                    () -> "the test wand is not holly + unicorn hair");
            Float length = wand.get(WandComponents.WAND_LENGTH.get());
            check(helper, length != null && Math.abs(length - 10.5f) < 1e-4, () -> "length " + length);
            check(helper, Optional.empty().equals(wand.get(WandComponents.WAND_MASTER.get())),
                    () -> "the test wand came bonded to someone: " + wand.get(WandComponents.WAND_MASTER.get()));
            check(helper, wand.get(WandComponents.WAND_CONFIGURATION.get()) != null, () -> "the requested look was not applied");
            // The stats of the given wand are what the preview resolved: the same resolver a cast uses.
            check(helper, WandAdminService.preview(server, make(HOLLY, UNICORN, 10.5f, "PLIANT", "")).valid(),
                    () -> "preview and give disagree");
            helper.succeed();
        } finally {
            if (opped[0]) {
                server.getPlayerList().deop(wandmaker.nameAndId());
            }
            WizardTestSupport.retire(helper, wandmaker);
        }
    }

    // ── brooms ──

    private static void broomValuesReachDefinitions(GameTestHelper helper) {
        BroomDefinition authored = BroomRules.authored(NIMBUS);
        if (authored == null) {
            helper.fail("the Nimbus 2000 definition is not loaded");
            return;
        }
        Identifier maxSpeed = BroomSettingProvider.id(NIMBUS, "max_speed");
        Identifier schoolEnabled = BroomSettingProvider.id(SCHOOL_BROOM, BroomSettingProvider.ENABLED);
        Identifier scale = Identifier.fromNamespaceAndPath(NS, "broom_server_speed_scale");
        Runnable flight = WizardTestSupport.leaseModule(Module.BROOM_FLIGHT);
        ServerPlayer rider = WizardTestSupport.placeMockPlayer(helper, "BroomAdminRider", GameType.SURVIVAL);
        try {
            int generation = BroomDefinitionRegistry.generation();
            AdminResult faster = service().change(CONSOLE, maxSpeed, "1.5", true);
            check(helper, faster.applied(), () -> "the speed override was refused: " + faster);
            check(helper, Math.abs(BroomDefinitionRegistry.get(NIMBUS).maxSpeed() - 1.5f) < 1e-4,
                    () -> "the synced definition flies at " + BroomDefinitionRegistry.get(NIMBUS).maxSpeed());
            check(helper, BroomDefinitionRegistry.generation() != generation, () -> "the registry generation did not move");
            check(helper, Math.abs(BroomRules.authored(NIMBUS).maxSpeed() - authored.maxSpeed()) < 1e-6,
                    () -> "the authored definition was edited");

            AdminResult outOfBounds = service().change(CONSOLE, maxSpeed, "9.0", true);
            check(helper, !outOfBounds.applied(), () -> "a speed past the slider bound was stored: " + outOfBounds);

            AdminResult half = service().change(CONSOLE, scale, "0.5", true);
            check(helper, half.applied(), () -> "the speed scale was refused: " + half);
            check(helper, Math.abs(BroomDefinitionRegistry.get(NIMBUS).maxSpeed() - 0.75f) < 1e-4,
                    () -> "override × scale should fly at 0.75, flies at " + BroomDefinitionRegistry.get(NIMBUS).maxSpeed());
            service().reset(CONSOLE, scale, true);
            service().reset(CONSOLE, maxSpeed, true);
            check(helper, Math.abs(BroomDefinitionRegistry.get(NIMBUS).maxSpeed() - authored.maxSpeed()) < 1e-6,
                    () -> "reset did not restore the authored speed");

            // The school broom needs no licence, so with flight on the only thing that can refuse it is the withdrawal;
            // the same use deploying it once allowed again proves that.
            AdminResult off = service().change(CONSOLE, schoolEnabled, "false", true);
            check(helper, off.applied() && !BroomRules.enabled(SCHOOL_BROOM), () -> "withdrawing the broom failed: " + off);
            Item school = BuiltInRegistries.ITEM.getValue(SCHOOL_BROOM);
            rider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(school));
            InteractionResult use = rider.getMainHandItem().use(helper.getLevel(), rider, InteractionHand.MAIN_HAND);
            AABB around = new AABB(rider.blockPosition()).inflate(8);
            check(helper, use == InteractionResult.FAIL, () -> "a withdrawn broom answered " + use);
            check(helper, helper.getLevel().getEntitiesOfClass(BroomEntity.class, around).isEmpty(),
                    () -> "a withdrawn broom was deployed");
            check(helper, !rider.getMainHandItem().isEmpty(), () -> "the withdrawn broom's item was taken");

            service().reset(CONSOLE, schoolEnabled, true);
            InteractionResult allowed = rider.getMainHandItem().use(helper.getLevel(), rider, InteractionHand.MAIN_HAND);
            // Deploying hands the item over to the broom entity; the empty hand is the proof.
            check(helper, allowed != InteractionResult.FAIL && rider.getMainHandItem().isEmpty(),
                    () -> "the allowed broom was still not deployed (" + allowed + "); the refusal above proved nothing");
            helper.succeed();
        } finally {
            service().reset(CONSOLE, scale, true);
            service().reset(CONSOLE, maxSpeed, true);
            service().reset(CONSOLE, schoolEnabled, true);
            if (rider.getVehicle() instanceof BroomEntity mounted) {
                rider.stopRiding();
                mounted.discard();
            }
            helper.getLevel().getEntitiesOfClass(BroomEntity.class, new AABB(rider.blockPosition()).inflate(32)).forEach(BroomEntity::discard);
            flight.run();
            WizardTestSupport.retire(helper, rider);
        }
    }

    private static void speedGuardSetsDownOverspeeder(GameTestHelper helper) {
        // The broom swings four blocks out: keep every chunk it can reach ticking, or it simply stops being observed.
        BlockPos min = BROOM_AT.offset(-2, 0, -2);
        BlockPos max = BROOM_AT.offset(6, 0, 2);
        WizardTestSupport.forceChunks(helper, min, max);
        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> runOverspeed(helper));
    }

    private static void runOverspeed(GameTestHelper helper) {
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), BROOM_AT);
        ServerPlayer rider = WizardTestSupport.placeMockPlayer(helper, "BroomAdminSpeeder");
        rider.startRiding(broom);
        double homeX = broom.getX();
        // Four blocks a tick, back and forth: far past any shipped broom's top speed, and never leaving the test area.
        for (int tick = 1; tick <= 16; tick++) {
            boolean out = tick % 2 == 1;
            helper.runAfterDelay(tick, () -> {
                if (rider.getVehicle() == broom) {
                    broom.absSnapTo(out ? homeX + 4.0 : homeX, broom.getY(), broom.getZ(), broom.getYRot(), broom.getXRot());
                }
            });
        }
        helper.runAfterDelay(18, () -> {
            try {
                check(helper, rider.getVehicle() != broom, () -> "a broom moving 4 blocks a tick kept its rider");
                check(helper, broom.isAlive(), () -> "the guard destroyed the broom; it should only set the rider down");
                helper.succeed();
            } finally {
                WizardTestSupport.retire(helper, rider);
                broom.discard();
            }
        });
    }
}
