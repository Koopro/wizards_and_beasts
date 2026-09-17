package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.render.outline.BlockOutlineService;
import at.koopro.wizardsandbeasts.render.outline.EntityOutlineService;
import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import at.koopro.wizardsandbeasts.render.outline.OutlineStyle;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.revelio.Revelio;
import at.koopro.wizardsandbeasts.spell.revelio.RevelioScan;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.NullMarked;

/**
 * Revelio against a real world: what it finds, what it must not, and that a cast reaches both services.
 *
 * <p>Only a running server has tags, block entities and a {@code clip} to ask. Each probe sits on its own
 * axis from the caster, so no probe's ray passes through another probe:
 * <pre>
 *                 +z: chest (seen)
 *   -x: iron ore (seen)          caster          +x: stone wall | chest (hidden) | cow (hidden)
 *   -x-z: sign (ignored)         -z: cow (seen)  +x-z: plain stone (seen, not interesting)
 * </pre>
 *
 * <h2>High above everything else</h2>
 *
 * <p>Game tests share one world, their origins six or seven blocks apart, and this fixture is twelve blocks
 * wide. A real cast also outlines every mob it can see for seven seconds, which at ground level would include
 * {@code TimedOutlineTests}' cow — whose whole assertion is that its outline is gone after twenty ticks. So
 * each scenario is built at its own height, more than Revelio's radius above anything another scenario
 * touches ({@code AguamentiTests} clears and floods columns up to y 40) and above each other.
 */
@NullMarked
public final class RevelioTests {

    private static final int SCAN_HEIGHT = 64;
    private static final int CAST_HEIGHT = 88;

    private static final BlockPos SEEN_CHEST = new BlockPos(0, 2, 3);
    private static final BlockPos SEEN_ORE = new BlockPos(-3, 2, 0);
    private static final BlockPos IGNORED_SIGN = new BlockPos(-3, 2, -3);
    private static final BlockPos DULL_STONE = new BlockPos(3, 2, -3);
    private static final BlockPos WALL_LOW = new BlockPos(4, 2, 0);
    private static final BlockPos WALL_HIGH = new BlockPos(4, 3, 0);
    private static final BlockPos HIDDEN_CHEST = new BlockPos(6, 2, 0);
    private static final BlockPos SEEN_COW = new BlockPos(0, 2, -4);
    private static final BlockPos HIDDEN_COW = new BlockPos(8, 2, 0);
    private static final BlockPos SPECTATOR = new BlockPos(3, 1, 3);

    private RevelioTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("revelio_finds_what_the_caster_can_see",
                "revelio: sees chests, ores and mobs in view; ignores signs, stone, and anything behind a wall",
                RevelioTests::findsWhatTheCasterCanSee);
        tests.add("revelio_cast_outlines_through_both_services",
                "revelio: the shipped spell is castable and hands its finds to the entity and block outline services",
                RevelioTests::castOutlinesThroughBothServices);
    }

    /** One scenario's layout, every position relative to the test and already raised to its height. */
    private record Fixture(GameTestHelper helper, int height, ServerPlayer caster, Entity seenCow, Entity hiddenCow) {

        BlockPos at(BlockPos probe) {
            return helper.absolutePos(probe.above(height));
        }

        static Fixture build(GameTestHelper helper, String name, int height) {
            ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name);
            // parkAtOrigin, raised: eyes about 2.6 above the stand, level with the probes' centres.
            BlockPos stand = helper.absolutePos(BlockPos.ZERO.above(height));
            caster.teleportTo(stand.getX() + 0.5, stand.getY() + 1, stand.getZ() + 0.5);
            caster.setNoGravity(true);

            place(helper, height, SEEN_CHEST, Blocks.CHEST);
            place(helper, height, SEEN_ORE, Blocks.IRON_ORE);
            place(helper, height, DULL_STONE, Blocks.STONE);
            place(helper, height, WALL_LOW, Blocks.STONE);
            place(helper, height, WALL_HIGH, Blocks.STONE);
            place(helper, height, HIDDEN_CHEST, Blocks.CHEST);
            // Last, with nothing placed beside it afterwards: a standing sign over air survives only until a
            // neighbour update asks it to check its support.
            place(helper, height, IGNORED_SIGN, Blocks.OAK_SIGN);

            Entity seenCow = helper.spawn(EntityType.COW, SEEN_COW.above(height));
            Entity hiddenCow = helper.spawn(EntityType.COW, HIDDEN_COW.above(height));
            seenCow.setNoGravity(true);
            hiddenCow.setNoGravity(true);
            return new Fixture(helper, height, caster, seenCow, hiddenCow);
        }

        private static void place(GameTestHelper helper, int height, BlockPos probe, net.minecraft.world.level.block.Block block) {
            helper.setBlock(probe.above(height), block);
        }

        void tearDown() {
            seenCow.discard();
            hiddenCow.discard();
            WizardTestSupport.retire(helper, caster);
        }
    }

    private static void findsWhatTheCasterCanSee(GameTestHelper helper) {
        Fixture fixture = Fixture.build(helper, "revelio_scanner", SCAN_HEIGHT);
        // Without this the "sign is ignored" check would pass just as well against a sign that had popped off.
        WizardTestSupport.check(helper, helper.getLevel().getBlockState(fixture.at(IGNORED_SIGN)).is(Blocks.OAK_SIGN),
                () -> "the sign fixture did not survive placement, so the ignore tag is untested");

        RevelioScan.Result found = RevelioScan.scan(helper.getLevel(), fixture.caster(), Revelio.DEFAULT_RADIUS);

        WizardTestSupport.check(helper, found.blocks().contains(fixture.at(SEEN_CHEST)),
                () -> "a chest in plain view was not found; the block-entity rule or the ray is wrong. Found "
                        + found.blocks());
        WizardTestSupport.check(helper, found.blocks().contains(fixture.at(SEEN_ORE)),
                () -> "an ore in plain view was not found; #wizards_and_beasts:revelio_reveals did not load");
        WizardTestSupport.check(helper, !found.blocks().contains(fixture.at(IGNORED_SIGN)),
                () -> "a sign was revealed; #wizards_and_beasts:revelio_ignores did not load");
        WizardTestSupport.check(helper, !found.blocks().contains(fixture.at(DULL_STONE)),
                () -> "plain stone was revealed");
        WizardTestSupport.check(helper, !found.blocks().contains(fixture.at(HIDDEN_CHEST)),
                () -> "a chest behind a stone wall was revealed; the scan sees through walls");

        WizardTestSupport.check(helper, found.entities().contains(fixture.seenCow()),
                () -> "a cow in plain view was not found. Found " + found.entities());
        WizardTestSupport.check(helper, !found.entities().contains(fixture.hiddenCow()),
                () -> "a cow behind a stone wall was found; the scan sees through walls");
        WizardTestSupport.check(helper, !found.entities().contains(fixture.caster()),
                () -> "the caster revealed themselves");

        // A spectator in plain view is not a hidden person; outlining one would be the charm seeing the camera.
        ServerPlayer spectator = WizardTestSupport.placeMockPlayer(helper, "revelio_spectator", GameType.SPECTATOR);
        BlockPos spectatorAt = fixture.at(SPECTATOR);
        spectator.teleportTo(spectatorAt.getX() + 0.5, spectatorAt.getY(), spectatorAt.getZ() + 0.5);
        spectator.setNoGravity(true);
        RevelioScan.Result withSpectator = RevelioScan.scan(helper.getLevel(), fixture.caster(), Revelio.DEFAULT_RADIUS);
        WizardTestSupport.check(helper, !withSpectator.entities().contains(spectator),
                () -> "a spectator was revealed");
        WizardTestSupport.retire(helper, spectator);

        fixture.tearDown();
        helper.succeed();
    }

    private static void castOutlinesThroughBothServices(GameTestHelper helper) {
        Spell revelio = Spells.byId("revelio");
        if (revelio == null) {
            helper.fail("revelio is not registered; spells/revelio.json did not load");
            return;
        }
        WizardTestSupport.check(helper, revelio.isImplemented(),
                () -> "revelio is registered but still refused at the cast gate");

        Fixture fixture = Fixture.build(helper, "revelio_caster", CAST_HEIGHT);
        boolean revealed = Revelio.cast(helper.getLevel(), fixture.caster(), revelio);

        WizardTestSupport.check(helper, revealed, () -> "a cast with a chest and a cow in view reported nothing");
        // The mock player is creative, so this is also the creative-caster case.
        OutlineEntry cowOutline = EntityOutlineService.snapshot().get(fixture.seenCow().getUUID());
        int expected = OutlineStyle.forSpell(revelio.getColor(), 1).argb();
        WizardTestSupport.check(helper, cowOutline != null,
                () -> "the cow in view carries no timed outline");
        WizardTestSupport.check(helper, cowOutline != null && cowOutline.argb() == expected,
                () -> "the cow's outline is not the spell's colour: expected " + Integer.toHexString(expected)
                        + ", got " + (cowOutline == null ? "none" : Integer.toHexString(cowOutline.argb())));
        WizardTestSupport.check(helper,
                !EntityOutlineService.snapshot().containsKey(fixture.hiddenCow().getUUID()),
                () -> "the cow behind the wall was outlined");
        WizardTestSupport.check(helper, BlockOutlineService.activeCount(fixture.caster().getUUID()) == 1,
                () -> "the caster was sent no block highlight");

        fixture.tearDown();
        helper.succeed();
    }
}
