package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.spell.lib.AguamentiHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * Aguamenti puts water where the jet ends — into a brewing pot, into the soil under a crop, against a wall — and
 * never onto the wizard holding it.
 *
 * <p>Each scenario drives a real hold on a real wand, ticking the mock caster by hand the way
 * {@link LeviosaThrowTests} does, and asserts on the world: the pot's water, the soil's moisture, and every fluid
 * cell around the caster. A long hold is three source-placement windows and then some, which is how far the old aim
 * needed to walk its placements back into the caster's face.
 */
public final class AguamentiTests {

    private static final String SPELL_ID = "aguamenti";
    /** {@code aguamenti} declares a {@code knows} requirement on this. */
    private static final String PREREQUISITE_ID = "lumos";

    /** Caster's feet, facing +Z down the lane. A standing player's eyes are 1.62 above, in {@link #HEAD}. */
    private static final Vec3 CASTER = new Vec3(1.5, 1.0, 0.5);
    private static final double EYE_HEIGHT = 1.62;
    private static final BlockPos HEAD = new BlockPos(1, 2, 0);

    private static final int LONG_HOLD_TICKS = AguamentiHelper.AGUAMENTI_MIN_HOLD_FOR_SOURCE * 3 + 10;
    /** Top of the cleared column and of the search for stray water: past the jet's reach straight up. */
    private static final int SKY_TOP = 40;

    private AguamentiTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("aguamenti_fills_a_brewing_cauldron", "aguamenti: fills an empty brewing pot",
                AguamentiTests::fillsABrewingCauldron);
        tests.add("aguamenti_waters_farmland_under_a_crop", "aguamenti: waters the soil a crop stands in",
                AguamentiTests::watersFarmlandUnderACrop);
        tests.add("aguamenti_at_open_sky_pours_nothing", "aguamenti: a jet that hits nothing places nothing",
                AguamentiTests::atOpenSkyPoursNothing);
        tests.add("aguamenti_under_a_low_ceiling_spares_the_caster", "aguamenti: no source over the caster",
                AguamentiTests::underALowCeilingSparesTheCaster);
        tests.add("aguamenti_at_a_wall_places_one_source", "aguamenti: a long hold at a wall places one source",
                AguamentiTests::atAWallPlacesOneSource);
    }

    // ── the brewing pot ─────────────────────────────────────────────────────────────────────────

    private static void fillsABrewingCauldron(GameTestHelper helper) {
        clearTheLane(helper, 4);
        BlockPos potPos = new BlockPos(1, 2, 2);
        helper.setBlock(potPos.below(), Blocks.STONE);
        helper.setBlock(potPos, ModBlocks.PEWTER_CAULDRON.get());
        CauldronBlockEntity pot = cauldronAt(helper, potPos);
        ServerPlayer caster = readyCaster(helper, "wandb-aguamenti-brewer", 0.0f);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(LONG_HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkStillHolding(helper, caster);
                    check(helper, pot.isFilled(),
                            () -> "a " + LONG_HOLD_TICKS + "-tick jet into an empty brewing pot left it dry: "
                                    + describe(caster));
                    List<String> water = fluidCells(helper, false);
                    check(helper, water.isEmpty(),
                            () -> "filling the pot also placed water: " + water + " | " + describe(caster));
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── the field ───────────────────────────────────────────────────────────────────────────────

    private static void watersFarmlandUnderACrop(GameTestHelper helper) {
        clearTheLane(helper, 4);
        BlockPos soil = new BlockPos(1, 1, 2);
        BlockPos crop = soil.above();
        helper.setBlock(soil, Blocks.FARMLAND);
        helper.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(CropBlock.MAX_AGE));
        // Aimed through the crop at the middle of the soil's top face. A ripe crop's outline fills its cell, so an
        // outline ray stops on the wheat and never reaches the farmland; the beam's collider ray passes through.
        Vec3 soilTop = new Vec3(1.5, 1.0 + 15.0 / 16.0, 2.5);
        float pitch = (float) Math.toDegrees(Math.atan2(CASTER.y + EYE_HEIGHT - soilTop.y, soilTop.z - CASTER.z));
        ServerPlayer caster = readyCaster(helper, "wandb-aguamenti-farmer", pitch);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(LONG_HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    // One more channel tick inside this server tick, so no random tick can dry the soil a step
                    // between the jet and the read.
                    caster.doTick();
                    checkStillHolding(helper, caster);
                    BlockState soilState = helper.getBlockState(soil);
                    check(helper, soilState.is(Blocks.FARMLAND)
                                    && soilState.getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE,
                            () -> "the soil under the crop is " + soilState + " after the jet, expected fully wet "
                                    + "farmland: " + describe(caster));
                    check(helper, helper.getBlockState(crop).is(Blocks.WHEAT),
                            () -> "the crop is gone after the jet, the cell is now " + helper.getBlockState(crop)
                                    + ": " + describe(caster));
                    List<String> water = fluidCells(helper, false);
                    check(helper, water.isEmpty(),
                            () -> "watering the field placed water on it: " + water + " | " + describe(caster));
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── the caster stays dry ────────────────────────────────────────────────────────────────────

    /** The reported bug: nothing to hit, and the hold put a source one block from the caster's eyes. */
    private static void atOpenSkyPoursNothing(GameTestHelper helper) {
        clearTheLane(helper, SKY_TOP);
        ServerPlayer caster = readyCaster(helper, "wandb-aguamenti-skyward", -90.0f);
        holdThenCheckDry(helper, caster, "a jet straight up at open sky");
    }

    /** The near side of a low ceiling is the cell right over the caster's head. */
    private static void underALowCeilingSparesTheCaster(GameTestHelper helper) {
        clearTheLane(helper, 4);
        helper.setBlock(HEAD.above(2), Blocks.STONE);
        ServerPlayer caster = readyCaster(helper, "wandb-aguamenti-cellar", -90.0f);
        holdThenCheckDry(helper, caster, "a jet at the ceiling over the caster");
    }

    /**
     * One source, against the wall. The old aim saw water, so each source it placed became the next thing the jet
     * hit, and the one after went a block nearer — three windows reached the caster's head.
     */
    private static void atAWallPlacesOneSource(GameTestHelper helper) {
        clearTheLane(helper, 4);
        BlockPos wall = new BlockPos(1, 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        ServerPlayer caster = readyCaster(helper, "wandb-aguamenti-mason", 0.0f);
        String expected = wall.north().toShortString();

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(LONG_HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkStillHolding(helper, caster);
                    List<String> sources = fluidCells(helper, true);
                    check(helper, sources.equals(List.of(expected)),
                            () -> "a " + LONG_HOLD_TICKS + "-tick hold at a wall should leave one source, at "
                                    + expected + "; found " + sources + " | " + describe(caster));
                    check(helper, helper.getLevel().getFluidState(helper.absolutePos(HEAD)).isEmpty(),
                            () -> "water reached the caster's head: " + describe(caster));
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    private static void holdThenCheckDry(GameTestHelper helper, ServerPlayer caster, String what) {
        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(LONG_HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkStillHolding(helper, caster);
                    List<String> water = fluidCells(helper, false);
                    check(helper, water.isEmpty(),
                            () -> what + ", held " + LONG_HOLD_TICKS + " ticks, placed water: " + water
                                    + " | " + describe(caster));
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    /**
     * Stone underfoot and air up to {@code top}, from the caster to past anything a scenario builds.
     *
     * <p>The empty structure is walled in barriers, which are colliders: left in place, the jet would hit one and
     * a long hold would place its source against the barrier instead of against what the scenario built.
     */
    private static void clearTheLane(GameTestHelper helper, int top) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= top; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static ServerPlayer readyCaster(GameTestHelper helper, String name, float pitch) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.makeWandkind(caster);
        WizardTestSupport.giveBondedWand(caster);
        WizardTestSupport.learnAndSelect(helper, caster, SPELL_ID, PREREQUISITE_ID);
        Vec3 feet = helper.absoluteVec(CASTER);
        // Yaw 0 looks along +Z; pitch is positive looking down.
        caster.snapTo(feet.x, feet.y, feet.z, 0.0f, pitch);
        caster.setYHeadRot(0.0f);
        caster.setNoGravity(true);
        return caster;
    }

    /** The server's own entry for a use-item packet, so vanilla's preconditions stay in the path. */
    private static void beginCast(GameTestHelper helper, ServerPlayer caster) {
        caster.gameMode.useItem(caster, helper.getLevel(), caster.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static void checkStillHolding(GameTestHelper helper, ServerPlayer caster) {
        check(helper, caster.isUsingItem(),
                () -> "the hold ended before " + LONG_HOLD_TICKS + " ticks, so nothing here watched a live jet: "
                        + describe(caster));
    }

    private static CauldronBlockEntity cauldronAt(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof CauldronBlockEntity pot)) {
            helper.fail("no CauldronBlockEntity at the placed pewter cauldron; found "
                    + (blockEntity == null ? "nothing" : blockEntity.getClass().getSimpleName()));
            throw new IllegalStateException("unreachable");
        }
        return pot;
    }

    /** Every fluid cell — or only the sources — in and around the lane, up to {@link #SKY_TOP}, as test-relative positions. */
    private static List<String> fluidCells(GameTestHelper helper, boolean sourcesOnly) {
        List<String> found = new ArrayList<>();
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 5; z++) {
                for (int y = 0; y <= SKY_TOP; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    FluidState fluid = helper.getLevel().getFluidState(helper.absolutePos(pos));
                    if (!fluid.isEmpty() && (!sourcesOnly || fluid.isSource())) {
                        found.add(pos.toShortString());
                    }
                }
            }
        }
        return found;
    }

    private static String describe(ServerPlayer caster) {
        return "usingItem=" + caster.isUsingItem()
                + " activeSpell=" + spellData(caster).getActiveSpellId()
                + " aim=" + caster.getLookAngle()
                + " eye=" + caster.getEyePosition();
    }
}
