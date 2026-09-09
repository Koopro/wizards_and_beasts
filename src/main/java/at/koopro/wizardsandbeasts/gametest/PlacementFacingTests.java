package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.dummy.DuellingDummyEntity;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.spell.teacher.SpellTeacherBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Placing a duelling dummy or a spell teacher points it back at whoever placed it.
 *
 * <p>Both were reported facing one fixed direction however they were put down, and the two had
 * different causes a screenshot cannot tell apart:
 *
 * <ul>
 *   <li>the <b>spell teacher</b> had no {@code FACING} property at all — one blockstate variant,
 *       one orientation, and a lectern model with a tilted desk that therefore always tilted
 *       north;</li>
 *   <li>the <b>dummy</b> had correct placement code that could not work. {@code Entity.snapTo}
 *       writes {@code yRot}; a {@code LivingEntity} is drawn from {@code yBodyRot}; and a mob that
 *       registers no goals never runs the tracking that brings one to the other.</li>
 * </ul>
 *
 * <p>So these drive the real use path — {@code ItemStack.useOn} for both — from a player actually
 * looking along each cardinal, and assert the <b>rendered</b> rotation rather than the field the
 * placing code happened to set. An assertion on {@code yRot} would have passed against the bug.
 */
public final class PlacementFacingTests {

    /** Every direction a horizontal placement can pick, so none of the four is special-cased. */
    private static final List<Direction> CARDINALS =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    /** Where the thing being placed goes, and where the player stands so as not to be in the way. */
    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos TARGET = new BlockPos(1, 1, 1);
    private static final Vec3 PLAYER_STAND = new Vec3(4.5, 1.0, 4.5);

    private PlacementFacingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("placement_dummy_faces_the_player_who_planted_it",
                "dummy: planting one from each cardinal turns its body back at the placer",
                PlacementFacingTests::dummyFacesThePlacer);
        tests.add("placement_spell_teacher_faces_the_player_who_placed_it",
                "spell teacher: placing one from each cardinal sets FACING back at the placer",
                PlacementFacingTests::spellTeacherFacesThePlacer);
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void dummyFacesThePlacer(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "DummyPlanter");
        try {
            for (Direction look : CARDINALS) {
                clearTheSite(helper);
                standLookingAlong(helper, player, look);

                ItemStack stack = new ItemStack(MiscItemRegistry.DUELLING_DUMMY.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                // The item's own useOn, not ItemStack.useOn: server-side the latter goes through
                // NeoForge's CommonHooks.onPlaceItemIntoWorld, which is the *block* placement path
                // and does not plant a non-BlockItem's entity here. useOn is the method under test.
                InteractionResult result = MiscItemRegistry.DUELLING_DUMMY.get().useOn(
                        new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                                stack, topFaceHit(helper)));
                WizardTestSupport.check(helper, result.consumesAction(),
                        () -> "planting while looking " + look + " was refused with " + result
                                + ", so nothing about facing was measured. " + obstruction(helper));

                DuellingDummyEntity dummy = onlyDummy(helper);
                float expected = look.getOpposite().toYRot();
                WizardTestSupport.check(helper, sameAngle(dummy.yBodyRot, expected),
                        () -> "planted while looking " + look + ": the dummy's body is at "
                                + dummy.yBodyRot + ", expected " + expected);
                WizardTestSupport.check(helper, sameAngle(dummy.getYHeadRot(), expected),
                        () -> "planted while looking " + look + ": the dummy's head is at "
                                + dummy.getYHeadRot() + ", expected " + expected);
                // The previous-tick value goes with it, or the first frame renders a spin from
                // south to wherever the dummy was actually put.
                WizardTestSupport.check(helper, sameAngle(dummy.yBodyRotO, expected),
                        () -> "the dummy's previous body rotation is " + dummy.yBodyRotO
                                + ", so it will visibly swing on its first frame");
                dummy.discard();
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void spellTeacherFacesThePlacer(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TeacherPlacer");
        try {
            for (Direction look : CARDINALS) {
                clearTheSite(helper);
                standLookingAlong(helper, player, look);

                ItemStack stack = new ItemStack(ModBlocks.SPELL_TEACHER.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                        stack, topFaceHit(helper)));

                BlockState placed = helper.getBlockState(TARGET);
                WizardTestSupport.check(helper, placed.is(ModBlocks.SPELL_TEACHER.get()),
                        () -> "the spell teacher did not place while looking " + look
                                + "; found " + placed);
                Direction facing = placed.getValue(SpellTeacherBlock.FACING);
                WizardTestSupport.check(helper, facing == look.getOpposite(),
                        () -> "placed while looking " + look + ": FACING is " + facing
                                + ", expected " + look.getOpposite());
                helper.setBlock(TARGET, Blocks.AIR);
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    // -- helpers -----------------------------------------------------------------------------------

    /**
     * A stone floor with two blocks of air above it.
     *
     * <p>The air is not redundant. {@link WizardTestSupport#EMPTY_STRUCTURE} has zero size, and the
     * game-test framework walls the area it gives you in **barrier** blocks — so the square a
     * placement wants is solid unless it is cleared, and both of these placements refuse to build
     * into a solid block. It presented as a facing bug: the dummy simply never appeared. Two blocks
     * because the dummy's footprint is 1.95 tall.
     */
    private static void clearTheSite(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(TARGET, Blocks.AIR);
        helper.setBlock(TARGET.above(), Blocks.AIR);
    }

    /**
     * Puts the player clear of the placement site, looking along {@code look}.
     *
     * <p>Standing them off to one side is not tidiness: the dummy refuses to plant into anything
     * solid and {@code Level.noCollision} counts entities, so a player standing on the target
     * square fails the placement and the test would read as a facing bug.
     */
    private static void standLookingAlong(GameTestHelper helper, ServerPlayer player, Direction look) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.snapTo(origin.getX() + PLAYER_STAND.x, origin.getY() + PLAYER_STAND.y,
                origin.getZ() + PLAYER_STAND.z, look.toYRot(), 0.0f);
        player.setYHeadRot(look.toYRot());
    }

    /** A click on the top face of the floor block, which is what both placements require. */
    private static BlockHitResult topFaceHit(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(FLOOR);
        return new BlockHitResult(
                new Vec3(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5), Direction.UP, abs, false);
    }

    /** What the dummy's footprint is actually colliding with, for a refusal that says nothing. */
    private static String obstruction(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(TARGET);
        StringBuilder sb = new StringBuilder("footprint at " + abs + ": ");
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos p = abs.above(dy);
            sb.append(p.getY()).append('=').append(helper.getLevel().getBlockState(p)).append(' ');
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                abs.getX() + 0.2, abs.getY(), abs.getZ() + 0.2,
                abs.getX() + 0.8, abs.getY() + 1.95, abs.getZ() + 0.8);
        sb.append("| noCollision(box)=").append(helper.getLevel().noCollision(box));
        sb.append(" | entities=").append(helper.getLevel()
                .getEntities((net.minecraft.world.entity.Entity) null, box, e -> true).size());
        return sb.toString();
    }

    private static DuellingDummyEntity onlyDummy(GameTestHelper helper) {
        List<DuellingDummyEntity> found = helper.getLevel().getEntitiesOfClass(
                DuellingDummyEntity.class, helper.getBounds().inflate(8.0));
        if (found.size() != 1) {
            helper.fail("expected exactly one dummy in the test area, found " + found.size()
                    + " — the placement was refused, so nothing about facing was measured");
        }
        return found.getFirst();
    }

    /** Yaw comparison that does not care whether an angle came back as -90 or 270. */
    private static boolean sameAngle(float actual, float expected) {
        return Math.abs(Mth.degreesDifference(actual, expected)) < 0.5f;
    }
}
