package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.brew.BrewSettingIds;
import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.tuning.BrewTuning;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Brewing section's server boundary against the live brew and recipe registries and the real cauldron and
 * drink paths. Every change is restored inside the same synchronous step, through the same service.
 */
public final class AdminBrewTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    private static final String PEPPERUP = "wizards_and_beasts:pepperup_potion";
    /** Its only behaviour is its effect list (a legacy effects brew). */
    private static final String WIGGENWELD = "wizards_and_beasts:wiggenweld_potion";
    private static final String FELIX = "wizards_and_beasts:felix_felicis";
    private static final BlockPos CAULDRON = new BlockPos(0, 1, 0);
    private static final BlockPos HEAT = new BlockPos(0, 0, 0);

    private AdminBrewTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_brew_disabled_brew_cannot_be_used",
                "admin brewing: a disabled brew is refused at the cauldron (nothing consumed) and does nothing when drunk; brewing off refuses every start",
                AdminBrewTests::disabledBrewCannotBeUsed);
        tests.add("admin_brew_invalid_effects_rejected",
                "admin brewing: unknown effects, bad numbers, duplicates, empty lists on effect-only brews and non-editable brews are refused",
                AdminBrewTests::invalidEffectsRejected);
        tests.add("admin_brew_effect_override_applies_on_drink",
                "admin brewing: an edited effect list is what a drink applies, and reset restores the datapack's",
                AdminBrewTests::effectOverrideAppliesOnDrink);
        tests.add("admin_brew_recipes_remain_valid",
                "admin brewing: recipe numbers stay in bounds, every recipe keeps its ingredients and output, and a changed recipe still brews",
                AdminBrewTests::recipesRemainValid);
        tests.add("admin_brew_unauthorised_rejected",
                "admin brewing: a non-admin can neither change a brew nor read the brew pages",
                AdminBrewTests::unauthorisedRejected);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static Identifier brewId(String brew, String property) {
        return BrewSettingIds.brew(brew, property);
    }

    private static CauldronBlockEntity heatedCauldron(GameTestHelper helper) {
        helper.setBlock(HEAT, Blocks.MAGMA_BLOCK);
        helper.setBlock(CAULDRON, ModBlocks.PEWTER_CAULDRON.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(CAULDRON));
        if (!(blockEntity instanceof CauldronBlockEntity pot)) {
            helper.fail("no cauldron block entity");
            throw new IllegalStateException("unreachable");
        }
        return pot;
    }

    private static void fillPepperup(CauldronBlockEntity pot) {
        pot.addIngredient(new ItemStack(Items.BLAZE_POWDER));
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.setFilled(true);
    }

    // ── enabled ──

    private static void disabledBrewCannotBeUsed(GameTestHelper helper) {
        ServerPlayer brewer = WizardTestSupport.placeMockPlayer(helper, "BrewAdminBrewer", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, brewer);
            CauldronBlockEntity pot = heatedCauldron(helper);
            fillPepperup(pot);

            AdminResult off = service().change(CONSOLE, brewId(PEPPERUP, BrewSettingIds.ENABLED), "false");
            check(helper, off.applied() && !BrewTuning.enabled(PEPPERUP), () -> "disabling the brew was not applied: " + off);
            CauldronBlockEntity.StartResult refused = pot.startBrewing(CauldronTier.PEWTER, brewer.getUUID());
            check(helper, refused == CauldronBlockEntity.StartResult.BREW_DISABLED, () -> "a disabled brew started: " + refused);
            check(helper, !pot.isEmptyOfIngredients(), () -> "a refused brew consumed its ingredients");

            ItemStack bottle = BrewItem.of(Brews.byId(PEPPERUP));
            bottle.finishUsingItem(helper.getLevel(), brewer);
            check(helper, !brewer.hasEffect(MobEffects.SPEED) && !brewer.hasEffect(MobEffects.FIRE_RESISTANCE),
                    () -> "a disabled brew applied its effects when drunk");
            check(helper, bottle.getCount() == 1, () -> "a disabled brew's bottle was used up");

            service().reset(CONSOLE, brewId(PEPPERUP, BrewSettingIds.ENABLED), false);
            Identifier brewing = Identifier.fromNamespaceAndPath("wizards_and_beasts", "brewing_enabled");
            AdminResult brewingOff = service().change(CONSOLE, brewing, "false", true);
            check(helper, brewingOff.applied(), () -> "switching brewing off was refused: " + brewingOff);
            try {
                CauldronBlockEntity.StartResult globally = pot.startBrewing(CauldronTier.PEWTER, brewer.getUUID());
                check(helper, globally == CauldronBlockEntity.StartResult.BREWING_DISABLED,
                        () -> "brewing started with the brewing rule off: " + globally);
            } finally {
                service().reset(CONSOLE, brewing, true);
            }
            CauldronBlockEntity.StartResult started = pot.startBrewing(CauldronTier.PEWTER, brewer.getUUID());
            check(helper, started == CauldronBlockEntity.StartResult.STARTED, () -> "the re-enabled brew did not start: " + started);
            helper.succeed();
        } finally {
            service().reset(CONSOLE, brewId(PEPPERUP, BrewSettingIds.ENABLED), false);
            WizardTestSupport.retire(helper, brewer);
        }
    }

    // ── effects ──

    private static void invalidEffectsRejected(GameTestHelper helper) {
        Identifier pepperupEffects = brewId(PEPPERUP, BrewSettingIds.EFFECTS);
        String before = service().registry().get(pepperupEffects) == null ? null
                : AdminSettings.registry().get(pepperupEffects).currentText();
        expect(helper, service().change(CONSOLE, pepperupEffects, "wizards_and_beasts:no_such_effect 600 0"), AdminRejection.INVALID_VALUE);
        expect(helper, service().change(CONSOLE, pepperupEffects, "minecraft:speed 600 99"), AdminRejection.INVALID_VALUE);
        expect(helper, service().change(CONSOLE, pepperupEffects, "minecraft:speed 99999999 0"), AdminRejection.INVALID_VALUE);
        expect(helper, service().change(CONSOLE, pepperupEffects, "minecraft:speed 600 0; minecraft:speed 20 1"), AdminRejection.INVALID_VALUE);
        expect(helper, service().change(CONSOLE, pepperupEffects, "speed for a while"), AdminRejection.INVALID_VALUE);
        expect(helper, service().change(CONSOLE, brewId(WIGGENWELD, BrewSettingIds.EFFECTS), ""), AdminRejection.CONFLICT);
        expect(helper, service().change(CONSOLE, brewId(FELIX, BrewSettingIds.EFFECTS), "minecraft:luck 600 0"), AdminRejection.UNKNOWN_SETTING);
        expect(helper, service().change(CONSOLE, brewId("wizards_and_beasts:no_such_brew", BrewSettingIds.ENABLED), "false"),
                AdminRejection.UNKNOWN_SETTING);
        String after = AdminSettings.registry().get(pepperupEffects).currentText();
        check(helper, after.equals(before), () -> "a refused change altered the effect list: " + after);
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, AdminResult result, AdminRejection expected) {
        check(helper, result.rejection() == expected, () -> "expected " + expected + " for " + result.settingId() + ", got " + result);
    }

    private static void effectOverrideAppliesOnDrink(GameTestHelper helper) {
        ServerPlayer drinker = WizardTestSupport.placeMockPlayer(helper, "BrewAdminDrinker", GameType.SURVIVAL);
        Identifier effects = brewId(WIGGENWELD, BrewSettingIds.EFFECTS);
        try {
            WizardTestSupport.parkAtOrigin(helper, drinker);
            // Strengthening is dangerous: it must arrive confirmed.
            AdminResult unconfirmed = service().change(CONSOLE, effects, "minecraft:luck 600 2; -minecraft:regeneration 200 1");
            check(helper, unconfirmed.needsConfirmation(), () -> "adding an effect did not ask for confirmation: " + unconfirmed);
            AdminResult edited = service().change(CONSOLE, effects, "minecraft:luck 600 2; -minecraft:regeneration 200 1", true);
            check(helper, edited.applied(), () -> "a valid effect list was refused: " + edited);

            Brew live = Brews.byId(WIGGENWELD);
            BrewEffect.ApplyEffects apply = BrewTuning.editableEffects(live);
            check(helper, apply != null && apply.effects().size() == 1 && apply.effects().get(0).id().getPath().equals("luck"),
                    () -> "the live brew does not carry the edited list");
            BrewItem.of(live).finishUsingItem(helper.getLevel(), drinker);
            check(helper, drinker.hasEffect(MobEffects.LUCK) && drinker.getEffect(MobEffects.LUCK).getAmplifier() == 2,
                    () -> "the drink did not apply the edited effect");
            check(helper, !drinker.hasEffect(MobEffects.REGENERATION), () -> "a disabled effect row was applied");

            // A bottle carries only a brew id: nothing on the stack can supply its own effect values.
            ItemStack forged = new ItemStack(at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry.BREW.get());
            forged.set(ModDataComponents.BREW_ID.get(), "wizards_and_beasts:no_such_brew");
            drinker.removeAllEffects();
            forged.finishUsingItem(helper.getLevel(), drinker);
            check(helper, drinker.getActiveEffects().isEmpty(), () -> "a bottle naming no real brew applied something");

            AdminResult reset = service().reset(CONSOLE, effects, false);
            check(helper, reset.applied() && BrewTuning.editableEffects(Brews.byId(WIGGENWELD)).effects().stream()
                            .anyMatch(spec -> spec.id().getPath().equals("regeneration")),
                    () -> "reset did not restore the datapack's effects");
            helper.succeed();
        } finally {
            service().reset(CONSOLE, effects, true);
            WizardTestSupport.retire(helper, drinker);
        }
    }

    // ── recipes ──

    private static void recipesRemainValid(GameTestHelper helper) {
        ServerPlayer brewer = WizardTestSupport.placeMockPlayer(helper, "BrewAdminRecipes", GameType.SURVIVAL);
        String recipe = PEPPERUP;
        Identifier heat = BrewSettingIds.recipe(recipe, BrewSettingIds.HEAT_TIME);
        Identifier sugar = BrewSettingIds.ingredient(recipe, "minecraft:sugar");
        Identifier tier = BrewSettingIds.recipe(recipe, BrewSettingIds.CAULDRON_TIER);
        try {
            WizardTestSupport.parkAtOrigin(helper, brewer);
            expect(helper, service().change(CONSOLE, heat, "5"), AdminRejection.OUT_OF_RANGE);
            expect(helper, service().change(CONSOLE, sugar, "0"), AdminRejection.OUT_OF_RANGE);
            expect(helper, service().change(CONSOLE, sugar, "65"), AdminRejection.OUT_OF_RANGE);
            expect(helper, service().change(CONSOLE, tier, "GOLD"), AdminRejection.INVALID_VALUE);
            expect(helper, service().change(CONSOLE, BrewSettingIds.recipe(recipe, BrewSettingIds.FAILURE_CHANCE), "1.5"),
                    AdminRejection.OUT_OF_RANGE);
            expect(helper, service().change(CONSOLE, BrewSettingIds.ingredient(recipe, "minecraft:diamond"), "2"),
                    AdminRejection.UNKNOWN_SETTING);

            check(helper, service().change(CONSOLE, heat, "600").applied(), () -> "a valid heat time was refused");
            check(helper, service().change(CONSOLE, sugar, "3").applied(), () -> "a valid ingredient count was refused");
            BrewingRecipe live = BrewingRecipes.byId(recipe);
            check(helper, live != null && live.heatTimeTicks() == 600 && live.outputBrewId().equals(PEPPERUP)
                            && live.ingredients().size() == 2, () -> "the recipe lost its shape: " + live);

            CauldronBlockEntity pot = heatedCauldron(helper);
            pot.addIngredient(new ItemStack(Items.BLAZE_POWDER));
            // One item per call, as the block feeds a pot from a player's hand.
            for (int i = 0; i < 3; i++) {
                pot.addIngredient(new ItemStack(Items.SUGAR));
            }
            pot.setFilled(true);
            CauldronBlockEntity.StartResult start = pot.startBrewing(CauldronTier.PEWTER, brewer.getUUID());
            check(helper, start == CauldronBlockEntity.StartResult.STARTED, () -> "the changed recipe no longer brews: " + start);
            check(helper, pot.totalTicks() == at.koopro.wizardsandbeasts.brew.tuning.BrewTuningService.heatTimeFor(live),
                    () -> "the pot did not take the changed heat time: " + pot.totalTicks());

            for (BrewingRecipe each : BrewingRecipes.all()) {
                check(helper, !each.ingredients().isEmpty() && Brews.byId(each.outputBrewId()) != null,
                        () -> "recipe " + each.id() + " lost its ingredients or its output");
            }
            helper.succeed();
        } finally {
            service().reset(CONSOLE, heat, false);
            service().reset(CONSOLE, sugar, false);
            service().reset(CONSOLE, tier, false);
            WizardTestSupport.retire(helper, brewer);
        }
    }

    // ── authority ──

    private static void unauthorisedRejected(GameTestHelper helper) {
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "BrewAdminOutsider", GameType.SURVIVAL);
        try {
            AdminResult change = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(1,
                    brewId(PEPPERUP, BrewSettingIds.EFFECTS), "minecraft:strength 72000 9", true));
            check(helper, change.rejection() == AdminRejection.UNAUTHORIZED, () -> "a non-admin changed a brew: " + change);
            AdminResult disable = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(2,
                    brewId(PEPPERUP, BrewSettingIds.ENABLED), "false", true));
            check(helper, disable.rejection() == AdminRejection.UNAUTHORIZED && BrewTuning.enabled(PEPPERUP),
                    () -> "a non-admin disabled a brew: " + disable);
            WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, !AdminBrewPayloads.sendList(outsider) && !AdminBrewPayloads.sendDetail(outsider, PEPPERUP),
                    () -> "a non-admin was sent brew pages");
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(outsider)) {
                check(helper, !(payload instanceof AdminBrewPayloads.ListReply) && !(payload instanceof AdminBrewPayloads.DetailReply),
                        () -> "a non-admin received " + payload.type().id());
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
        }
    }
}
