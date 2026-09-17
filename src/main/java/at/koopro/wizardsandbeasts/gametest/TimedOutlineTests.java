package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.render.outline.BlockOutlineService;
import at.koopro.wizardsandbeasts.render.outline.EntityOutlineService;
import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import at.koopro.wizardsandbeasts.render.outline.OutlineStyle;
import at.koopro.wizardsandbeasts.render.outline.SpellOutlines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Timed outlines — entity and block — against a running server, through {@link SpellOutlines} as a spell calls
 * them.
 *
 * <p>{@code EntityOutlineTest} and {@code BlockOutlineTest} own the expiry arithmetic with the clock passed in.
 * What only a server can answer is whether anything ever advances that clock — each service's
 * {@code ServerTickEvent} subscriber has to be on the bus and overworld game time has to move — and whether the
 * logout and dimension-change cleanups are actually wired to the events that fire.
 *
 * <p>The cleanup scenarios use a duration far longer than the test, so expiry cannot be what cleared them.
 */
@NullMarked
public final class TimedOutlineTests {

    private static final int DURATION_TICKS = 10;
    private static final int OUTLASTS_THE_TEST = 2_000;

    private TimedOutlineTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("timed_outline_expires_on_its_own",
                "outlines: a timed outline on a non-player entity comes off after its duration with no caller",
                TimedOutlineTests::expiresOnItsOwn);
        tests.add("block_highlight_reaches_a_player_and_expires",
                "outlines: a block highlight is sent over a real connection and ends after its duration",
                TimedOutlineTests::blockHighlightExpires);
        tests.add("outlines_leave_with_a_player_who_logs_out",
                "outlines: logging out drops the player's own outline and every highlight they were shown",
                TimedOutlineTests::logoutCleansUp);
        tests.add("block_highlights_do_not_follow_a_viewer_to_another_dimension",
                "outlines: changing dimension drops the viewer's block highlights on the server",
                TimedOutlineTests::dimensionChangeCleansUp);
    }

    private static void expiresOnItsOwn(GameTestHelper helper) {
        Entity cow = helper.spawn(EntityType.COW, new BlockPos(1, 1, 1));
        UUID id = cow.getUUID();
        OutlineStyle style = new OutlineStyle(0x33CCFF, DURATION_TICKS);

        SpellOutlines.highlightEntities(List.of(cow), style);
        OutlineEntry held = EntityOutlineService.snapshot().get(id);
        WizardTestSupport.check(helper, held != null && held.argb() == style.argb(),
                () -> "highlightEntities did not put the cow in the snapshot; got " + held);

        helper.runAfterDelay(3, () -> WizardTestSupport.check(helper,
                EntityOutlineService.snapshot().containsKey(id),
                () -> "the outline came off after 3 ticks of a " + DURATION_TICKS + "-tick duration"));

        helper.runAfterDelay(DURATION_TICKS * 2, () -> {
            WizardTestSupport.check(helper, !EntityOutlineService.snapshot().containsKey(id),
                    () -> "the outline is still held " + (DURATION_TICKS * 2) + " ticks into a "
                            + DURATION_TICKS + "-tick duration; nothing is calling tick()");
            cow.discard();
            helper.succeed();
        });
    }

    /**
     * Sending to a real {@code ServerPlayer} is the part a unit test cannot reach: an unregistered payload
     * type throws on send, which would surface here as a test error rather than as a silent nothing in game.
     */
    private static void blockHighlightExpires(GameTestHelper helper) {
        ServerPlayer viewer = WizardTestSupport.placeMockPlayer(helper, "highlight_viewer");
        WizardTestSupport.parkAtOrigin(helper, viewer);
        BlockPos floor = helper.absolutePos(BlockPos.ZERO);

        boolean sent = SpellOutlines.highlightBlocks(viewer,
                BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 0, 1)),
                new OutlineStyle(0xFFFFAA, DURATION_TICKS));
        WizardTestSupport.check(helper, sent, () -> "nine positions were refused as nothing to highlight");
        WizardTestSupport.check(helper, BlockOutlineService.activeCount(viewer.getUUID()) == 1,
                () -> "the server is not tracking the highlight it just sent");

        helper.runAfterDelay(DURATION_TICKS * 2, () -> {
            WizardTestSupport.check(helper, BlockOutlineService.activeCount(viewer.getUUID()) == 0,
                    () -> "the highlight is still live " + (DURATION_TICKS * 2) + " ticks into a "
                            + DURATION_TICKS + "-tick duration; nothing is calling tick()");
            WizardTestSupport.retire(helper, viewer);
            helper.succeed();
        });
    }

    private static void logoutCleansUp(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "outline_leaver");
        WizardTestSupport.parkAtOrigin(helper, player);
        OutlineStyle style = new OutlineStyle(0xFFFFAA, OUTLASTS_THE_TEST);
        SpellOutlines.highlightEntities(List.of(player), style);
        SpellOutlines.highlightBlocks(player, List.of(helper.absolutePos(BlockPos.ZERO)), style);
        UUID id = player.getUUID();
        WizardTestSupport.check(helper, EntityOutlineService.snapshot().containsKey(id)
                        && BlockOutlineService.activeCount(id) == 1,
                () -> "the fixture did not take: nothing was outlined before the logout");

        WizardTestSupport.retire(helper, player);

        WizardTestSupport.check(helper, !EntityOutlineService.snapshot().containsKey(id),
                () -> "a logged-out player is still outlined; every client would show it again on rejoin");
        WizardTestSupport.check(helper, BlockOutlineService.activeCount(id) == 0,
                () -> "a logged-out viewer still has " + BlockOutlineService.activeCount(id) + " live highlight(s)");
        helper.succeed();
    }

    private static void dimensionChangeCleansUp(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.fail("the game-test server has no Nether to travel to");
            return;
        }
        ServerPlayer viewer = WizardTestSupport.placeMockPlayer(helper, "outline_traveller");
        WizardTestSupport.parkAtOrigin(helper, viewer);
        SpellOutlines.highlightBlocks(viewer, List.of(helper.absolutePos(BlockPos.ZERO)),
                new OutlineStyle(0xFFFFAA, OUTLASTS_THE_TEST));
        WizardTestSupport.check(helper, BlockOutlineService.activeCount(viewer.getUUID()) == 1,
                () -> "the fixture did not take: no highlight before the dimension change");

        viewer.teleport(new TeleportTransition(nether, new Vec3(0.5, 64, 0.5), Vec3.ZERO, 0.0f, 0.0f,
                Set.of(), entity -> {}));

        WizardTestSupport.check(helper, viewer.level() == nether,
                () -> "the viewer never reached the Nether, so the cleanup is untested");
        WizardTestSupport.check(helper, BlockOutlineService.activeCount(viewer.getUUID()) == 0,
                () -> "highlights from the Overworld followed the viewer into the Nether");
        WizardTestSupport.retire(helper, viewer);
        helper.succeed();
    }
}
