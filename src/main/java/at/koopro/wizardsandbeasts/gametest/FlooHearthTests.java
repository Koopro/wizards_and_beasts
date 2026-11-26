package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.HearthFire;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPattern;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPatterns;
import at.koopro.wizardsandbeasts.floo.hearth.HearthStamp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The player-built Floo hearth in a real world (Floo remake P3/P4, 2026-11-26): built from its pattern, lit, a pinch
 * thrown, a traveller standing in the fire hopping to a second hearth; a broken build, a cold grate and a mined grate.
 * Built twenty blocks up from the test's corner so no neighbouring scenario's ground is in the way.
 */
@NullMarked
final class FlooHearthTests {

    private static final Identifier COTTAGE = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cottage");

    private FlooHearthTests() {
    }

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("floo_hearth_ritual_carries_one_traveller",
                "floo: a built cottage hearth is recognised; logs, a light and a pinch turn it green; a traveller in "
                        + "the fire lands on the hearthstone of the other hearth, which flares, and the pinch is spent",
                FlooHearthTests::ritualCarriesOneTraveller);
        tests.addAlone("floo_hearth_broken_build_loses_the_green",
                "floo: taking a mantel block out of a green hearth unmakes it and drops the fire to an ordinary one",
                FlooHearthTests::brokenBuildLosesTheGreen);
        tests.addAlone("floo_hearth_cold_grate_refuses_a_pinch",
                "floo: a pinch only takes in an ordinary fire; a cold grate and a green one refuse it",
                FlooHearthTests::coldGrateRefusesAPinch);
        tests.addAlone("floo_hearth_templates_hold_real_hearths",
                "floo: the Leaky Cauldron and the showcase hall, placed from their generated templates, each hold a "
                        + "grate the game recognises as the style the generator built",
                FlooHearthTests::templatesHoldRealHearths);
        tests.addAlone("floo_hearth_mined_grate_takes_its_address",
                "floo: breaking the grate removes the hearth's address from the network",
                FlooHearthTests::minedGrateTakesItsAddress);
    }

    private static BlockPos site(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 20, z));
    }

    /** A cottage hearth with its grate at {@code grate}, facing south, on the network as {@code name}, logs laid. */
    private static FlooGrateBlockEntity hearth(GameTestHelper helper, BlockPos grate, String name) {
        ServerLevel level = helper.getLevel();
        check(helper, HearthStamp.stamp(level, grate, Direction.SOUTH, COTTAGE), () -> "the cottage style is not loaded");
        FlooGrateBlockEntity be = (FlooGrateBlockEntity) level.getBlockEntity(grate);
        check(helper, be != null && be.formed(), () -> "a stamped cottage hearth was not recognised at " + grate);
        level.setBlock(grate, level.getBlockState(grate).setValue(FlooGrateBlock.FUEL, true), 3);
        FlooNetworkManager.get(level).register(name, level.dimension().identifier(), grate, true, Optional.empty());
        be.setNetworkAddress(name);
        be.setRegistered(true);
        be.setEnabled(true);
        return be;
    }

    private static void light(ServerLevel level, BlockPos grate) {
        level.setBlock(grate, level.getBlockState(grate).setValue(FlooGrateBlock.FIRE, HearthFire.WARM), 3);
    }

    private static HearthFire fire(ServerLevel level, BlockPos grate) {
        return level.getBlockState(grate).getValue(FlooGrateBlock.FIRE);
    }

    private static String suffix(GameTestHelper helper) {
        return Integer.toString(Math.floorMod(helper.getLevel().getGameTime(), 100000));
    }

    private static void ritualCarriesOneTraveller(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String from = "Testhearth West " + suffix(helper);
        String to = "Testhearth East " + suffix(helper);
        BlockPos a = site(helper, 3, 3);
        BlockPos b = site(helper, 12, 3);
        ServerPlayer traveller = null;
        try {
            FlooGrateBlockEntity origin = hearth(helper, a, from);
            FlooGrateBlockEntity dest = hearth(helper, b, to);
            light(level, a);
            check(helper, origin.throwPinch(level), () -> "a pinch in an ordinary fire did not take");
            check(helper, fire(level, a) == HearthFire.FLOO, () -> "the pinch did not turn the fire green");

            traveller = WizardTestSupport.placeMockPlayer(helper, "Hopper", UUID.randomUUID(), GameType.SURVIVAL);
            traveller.teleportTo(level, a.getX() + 0.5, a.getY() + 0.25, a.getZ() + 0.5, java.util.Set.of(), 0, 0, false);
            FlooTravelHandler.clearCooldown(traveller);
            FlooTravelHandler.beginTravel(traveller, to, true);
            ServerPlayer hopper = traveller;
            check(helper, FlooDeparture.isDeparting(hopper.getUUID()),
                    () -> "standing in the green fire and saying the address did not start a departure");
            for (int i = 0; i <= FlooDeparture.windupTicks() && FlooDeparture.isDeparting(hopper.getUUID()); i++) {
                FlooDeparture.tick(hopper);
            }
            BlockPos landed = hopper.blockPosition();
            check(helper, landed.equals(b.relative(Direction.SOUTH)),
                    () -> "the traveller landed at " + landed + ", not on the hearthstone in front of " + b);
            check(helper, fire(level, a) == HearthFire.WARM,
                    () -> "the pinch was not spent: the departure fire is " + fire(level, a));
            check(helper, fire(level, b) == HearthFire.FLOO && dest.greenTicks() > 0,
                    () -> "the arrival hearth did not flare green");
            helper.succeed();
        } finally {
            FlooNetworkManager.get(level).unregister(from);
            FlooNetworkManager.get(level).unregister(to);
            retire(helper, traveller);
        }
    }

    private static void brokenBuildLosesTheGreen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "Testhearth Broken " + suffix(helper);
        BlockPos grate = site(helper, 3, 3);
        try {
            FlooGrateBlockEntity be = hearth(helper, grate, name);
            light(level, grate);
            check(helper, be.throwPinch(level), () -> "the pinch did not take");
            HearthPattern pattern = HearthPatterns.get(COTTAGE).orElseThrow();
            HearthPattern.Cell mantel = pattern.cells().stream().filter(c -> c.symbol() == 'M').findFirst().orElseThrow();
            level.setBlock(HearthPattern.place(grate, Direction.SOUTH, mantel), Blocks.AIR.defaultBlockState(), 3);
            BlockState state = level.getBlockState(grate);
            check(helper, !be.recheck(level, state), () -> "a hearth missing a mantel block still counts as finished");
            check(helper, fire(level, grate) == HearthFire.WARM,
                    () -> "a broken hearth kept its green fire: " + fire(level, grate));
            helper.succeed();
        } finally {
            FlooNetworkManager.get(level).unregister(name);
        }
    }

    private static void coldGrateRefusesAPinch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "Testhearth Cold " + suffix(helper);
        BlockPos grate = site(helper, 3, 3);
        try {
            FlooGrateBlockEntity be = hearth(helper, grate, name);
            check(helper, !be.throwPinch(level) && fire(level, grate) == HearthFire.NONE,
                    () -> "a pinch took in a cold grate");
            light(level, grate);
            check(helper, be.throwPinch(level), () -> "a pinch did not take in an ordinary fire");
            check(helper, !be.throwPinch(level), () -> "a second pinch took in a fire already green");
            helper.succeed();
        } finally {
            FlooNetworkManager.get(level).unregister(name);
        }
    }

    private static void minedGrateTakesItsAddress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "Testhearth Mined " + suffix(helper);
        BlockPos grate = site(helper, 3, 3);
        try {
            hearth(helper, grate, name);
            check(helper, FlooNetworkManager.get(level).getEntry(name) != null, () -> "the hearth did not register");
            level.setBlock(grate, Blocks.AIR.defaultBlockState(), 3);
            check(helper, FlooNetworkManager.get(level).getEntry(name) == null,
                    () -> "the address outlived its grate");
            helper.succeed();
        } finally {
            FlooNetworkManager.get(level).unregister(name);
        }
    }

    /** Grate offsets in each generated template and the style each must read as (tools/floo_hearths.py placed them). */
    private static void templatesHoldRealHearths(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Expect(String template, BlockPos grate, String style) {}
        java.util.List<Expect> expects = java.util.List.of(
                new Expect("diagon_alley/leaky_cauldron/a", new BlockPos(5, 1, 1), "cottage"),
                new Expect("showcase/magic/a", new BlockPos(5, 1, 1), "castle"),
                new Expect("showcase/magic/a", new BlockPos(13, 1, 1), "atrium"),
                new Expect("showcase/magic/a", new BlockPos(20, 1, 1), "shop"));
        BlockPos origin = site(helper, 0, 0);
        String placedTemplate = "";
        for (Expect e : expects) {
            if (!e.template().equals(placedTemplate)) {
                var template = level.getStructureManager()
                        .get(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, e.template()));
                check(helper, template.isPresent(), () -> "no template " + e.template());
                template.get().placeInWorld(level, origin, origin,
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                        level.getRandom(), 2);
                placedTemplate = e.template();
            }
            BlockPos spot = origin.offset(e.grate());
            check(helper, level.getBlockState(spot).is(at.koopro.wizardsandbeasts.registry.ModBlocks.FLOO_GRATE.get()),
                    () -> e.template() + ": no grate at " + e.grate() + " but " + level.getBlockState(spot));
            FlooGrateBlockEntity be = (FlooGrateBlockEntity) level.getBlockEntity(spot);
            check(helper, be != null && be.recheck(level, level.getBlockState(spot)),
                    () -> e.template() + ": the hearth at " + e.grate() + " is not a finished build");
            String style = be.style().map(Identifier::getPath).orElse("none");
            check(helper, style.equals(e.style()),
                    () -> e.template() + ": the hearth at " + e.grate() + " reads as " + style + ", not " + e.style());
        }
        helper.succeed();
    }
}
