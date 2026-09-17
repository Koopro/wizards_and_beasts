package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/**
 * Broom flight against a real server: where the rider ends up, what flying costs, and what a broom does
 * when nobody is on it.
 *
 * <p>Each of these shipped wrong while every unit test passed, because each is a composition a unit test
 * cannot see. The seat arithmetic was internally consistent and still put every rider 0.6 blocks inside the
 * broom, because {@code Entity.positionRider} subtracts the passenger's own vehicle attachment. The
 * durability catch-all was a reasonable rule applied to a motion the server only ever observes from
 * packets. So these assert the consequence — where the player actually is, what the durability actually
 * reads, how far the broom actually moved — never the field the code under test sets.
 */
public final class BroomRideTests {

    /** Where the broom starts: inside the air {@link #clearTheSite} opens up, off the stone floor. */
    private static final BlockPos BROOM_AT = new BlockPos(1, 2, 1);
    /** Ticks of packet-shaped position steps. The old catch-all billed every other one of them. */
    private static final int JITTER_TICKS = 40;
    /** How long a let-go broom is watched. */
    private static final int SETTLE_TICKS = 20;
    /**
     * How far the old unridden fall — {@code -0.04} a tick under {@code 0.95} drag — carried a broom in
     * {@link #SETTLE_TICKS}: {@code 0.8 * (20 - 19 * (1 - 0.95^20))}.
     */
    private static final double OLD_FALL_BLOCKS = 6.25;

    private BroomRideTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("broom_rider_stands_on_the_broom_facing_along_it",
                "broom: a mounted player's feet are on the broom's position, body turned with it, box grown to theirs",
                BroomRideTests::riderStandsOnTheBroom);
        tests.add("broom_packet_jitter_costs_no_durability",
                "broom: position steps the server only learns from packets are not billed as crashes",
                BroomRideTests::packetJitterCostsNoDurability);
        tests.add("broom_let_go_settles_rather_than_drops",
                "broom: with nobody riding it, a broom in mid-air sinks gently instead of falling",
                BroomRideTests::letGoBroomSettles);
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void riderStandsOnTheBroom(GameTestHelper helper) {
        clearTheSite(helper, 5);
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), BROOM_AT);
        broom.setYRot(90.0f);
        float parkedHeight = broom.getBbHeight();
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "BroomRider");
        player.startRiding(broom);

        helper.runAfterDelay(3, () -> {
            try {
                WizardTestSupport.check(helper, player.getVehicle() == broom,
                        () -> "startRiding did not seat the player on the broom; nothing below was measured");
                double feetOffset = player.getY() - broom.getY();
                WizardTestSupport.check(helper, Math.abs(feetOffset) < 1.0e-6,
                        () -> "the rider's feet are " + feetOffset + " blocks from the broom's position. The seat "
                                + "must cancel the rider's own vehicle attachment exactly, or the rider is drawn "
                                + "that far into, or above, the broom");
                WizardTestSupport.check(helper, sameAngle(player.yBodyRot, broom.getYRot()),
                        () -> "the rider's body is at " + player.yBodyRot + " on a broom facing "
                                + broom.getYRot() + "; legs posed astride the shaft point across it");
                WizardTestSupport.check(helper, near(broom.getBbHeight(), player.getBbHeight()),
                        () -> "a ridden broom's box is " + broom.getBbHeight() + " tall against a "
                                + player.getBbHeight() + "-tall rider; it must be the rider's box, or their "
                                + "head flies into ceilings");

                player.stopRiding();
                WizardTestSupport.check(helper, near(broom.getBbHeight(), parkedHeight),
                        () -> "after dismounting the broom kept a " + broom.getBbHeight()
                                + "-tall box; a parked broom is " + parkedHeight + " tall");
                helper.succeed();
            } finally {
                WizardTestSupport.retire(helper, player);
                broom.discard();
            }
        });
    }

    private static void packetJitterCostsNoDurability(GameTestHelper helper) {
        clearTheSite(helper, 5);
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), BROOM_AT);
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "BroomJitter");
        player.startRiding(broom);
        int before = broom.getCurrentDurability();
        double homeX = broom.getX();

        for (int tick = 1; tick <= JITTER_TICKS; tick++) {
            boolean out = tick % 4 == 1;
            // One block out and straight back, then two still ticks. It is what a late packet and the doubled
            // one after it look like to a server that learns a ridden broom's position only from
            // ServerboundMoveVehiclePacket: the observed motion swings by a full block between ticks while the
            // broom goes nowhere, and never touches anything.
            helper.runAfterDelay(tick, () -> broom.absSnapTo(out ? homeX + 1.0 : homeX, broom.getY(),
                    broom.getZ(), broom.getYRot(), broom.getXRot()));
        }

        helper.runAfterDelay(JITTER_TICKS + 2, () -> {
            try {
                int after = broom.getCurrentDurability();
                WizardTestSupport.check(helper, broom.isAlive(),
                        () -> "the broom broke during " + JITTER_TICKS + " ticks of flying into nothing");
                WizardTestSupport.check(helper, after == before,
                        () -> JITTER_TICKS + " ticks of packet-shaped position steps cost " + (before - after)
                                + " durability. A broom that hit nothing must lose nothing.");
                helper.succeed();
            } finally {
                WizardTestSupport.retire(helper, player);
                broom.discard();
            }
        });
    }

    private static void letGoBroomSettles(GameTestHelper helper) {
        clearTheSite(helper, 11);
        BroomEntity broom = helper.spawn(ModEntities.BROOM.get(), new BlockPos(1, 9, 1));
        double startY = broom.getY();

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            try {
                double fell = startY - broom.getY();
                WizardTestSupport.check(helper, fell > 0.1,
                        () -> "a broom nobody is riding moved " + fell + " blocks in " + SETTLE_TICKS
                                + " ticks; it must settle rather than hang in the air");
                WizardTestSupport.check(helper, fell < OLD_FALL_BLOCKS * 0.6,
                        () -> "a let-go broom fell " + fell + " blocks in " + SETTLE_TICKS + " ticks, against "
                                + OLD_FALL_BLOCKS + " for the old plank-like fall; it must drift down");
                helper.succeed();
            } finally {
                broom.discard();
            }
        });
    }

    // -- helpers -----------------------------------------------------------------------------------

    /**
     * A stone floor with {@code height} blocks of air above it. Not tidiness: an empty structure is fenced in
     * barrier blocks, and a broom spawned inside them collides with every one.
     */
    private static void clearTheSite(GameTestHelper helper, int height) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= height; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static boolean sameAngle(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b)) < 1.0e-3f;
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0e-3f;
    }
}
