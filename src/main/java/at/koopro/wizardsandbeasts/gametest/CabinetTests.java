package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.artefact.cabinet.CabinetPairs;
import at.koopro.wizardsandbeasts.artefact.cabinet.CabinetService;
import at.koopro.wizardsandbeasts.artefact.cabinet.CabinetService.Result;
import at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlock;
import at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlockEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Vanishing Cabinet (Phase 3a): two cabinets linked by wand gesture carry their owner from one to the other,
 * keep strangers out until shared, refuse a blocked exit, and come apart when either is broken.
 */
public final class CabinetTests {

    /**
     * Both cabinets face north, doors at z 1. The whole scene stays within x 0..3, z 1..2: game-test columns are
     * only six blocks apart, and a floor or cabinet built into the next column lands in someone else's scenario.
     */
    private static final BlockPos A = new BlockPos(0, 1, 2);
    private static final BlockPos B = new BlockPos(3, 1, 2);

    private CabinetTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("artefact_cabinet_links_and_carries",
                "artefact: two Vanishing Cabinets linked by wand carry their owner out in front of the other, keep a "
                        + "stranger out until the owner shares them, and unlink only on a confirmed second gesture",
                CabinetTests::linksAndCarries);
        tests.add("artefact_cabinet_breaking_severs",
                "artefact: breaking either half of a linked Vanishing Cabinet dissolves the pair; the survivor is a "
                        + "plain cabinet again",
                CabinetTests::breakingSevers);
        tests.add("artefact_cabinet_blocked_exit_refuses",
                "artefact: a Vanishing Cabinet will not send anyone out of a partner whose way out is walled up",
                CabinetTests::blockedExitRefuses);
        tests.add("artefact_cabinet_ward_stops_arrival",
                "artefact: an anti-Apparition ward over the far cabinet stops a Vanishing Cabinet arrival",
                CabinetTests::wardStopsArrival);
    }

    private static void wardStopsArrival(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer owner = player(helper, "CabinetWarded");
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        net.minecraft.resources.Identifier wardId = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "test_cabinet_ward_" + a.asLong());
        try {
            floor(helper);
            place(helper, A, owner);
            place(helper, B, owner);
            CabinetService.gesture(owner, level, a);
            CabinetService.gesture(owner, level, helper.absolutePos(B));
            at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry.register(
                    new at.koopro.wizardsandbeasts.apparition.ApparitionWard(wardId, level.dimension().identifier(),
                            new net.minecraft.world.phys.AABB(helper.absolutePos(B.relative(Direction.NORTH))),
                            net.minecraft.network.chat.Component.literal("warded"), false));
            Vec3 before = owner.position();
            Result result = CabinetService.travel(owner, level, a);
            check(helper, result == Result.WARDED, () -> "a warded arrival gave: " + result);
            check(helper, owner.position().equals(before), () -> "the player moved anyway");
            helper.succeed();
        } finally {
            at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry.remove(wardId);
            CabinetPairs.get(level).sever(GlobalPos.of(level.dimension(), a));
            WizardTestSupport.retire(helper, owner);
            artefacts.run();
        }
    }

    private static void linksAndCarries(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer owner = player(helper, "CabinetOwner");
        ServerPlayer stranger = player(helper, "CabinetStranger");
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        BlockPos b = helper.absolutePos(B);
        try {
            floor(helper);
            place(helper, A, owner);
            place(helper, B, owner);

            check(helper, CabinetService.travel(owner, level, a) == Result.NOT_LINKED, () -> "an unlinked cabinet went somewhere");
            check(helper, CabinetService.gesture(stranger, level, a) == Result.NOT_OWNER,
                    () -> "a stranger could start linking someone else's cabinet");
            Result first = CabinetService.gesture(owner, level, a);
            check(helper, first == Result.ATTUNED, () -> "first gesture: " + first);
            Result second = CabinetService.gesture(owner, level, b);
            check(helper, second == Result.LINKED, () -> "second gesture: " + second);
            CabinetPairs.Pair pair = CabinetPairs.get(level).pairAt(GlobalPos.of(level.dimension(), a));
            check(helper, pair != null && GlobalPos.of(level.dimension(), b).equals(pair.partnerOf(GlobalPos.of(level.dimension(), a))),
                    () -> "the registry does not hold A <-> B: " + pair);

            Result trip = CabinetService.travel(owner, level, a);
            check(helper, trip == Result.TRAVELLED, () -> "the owner did not go through: " + trip);
            Vec3 door = Vec3.atBottomCenterOf(b.relative(Direction.NORTH));
            check(helper, owner.position().distanceTo(door) < 0.01,
                    () -> "the owner came out at " + owner.position() + ", not in front of B at " + door);
            check(helper, CabinetService.travel(owner, level, b) == Result.COOLDOWN, () -> "no pause between trips");

            check(helper, CabinetService.travel(stranger, level, a) == Result.PRIVATE_PAIR, () -> "a stranger used a private pair");
            check(helper, CabinetService.toggleShared(stranger, level, a) == Result.NOT_OWNER, () -> "a stranger shared the pair");
            check(helper, CabinetService.toggleShared(owner, level, b) == Result.SHARED, () -> "the owner could not share");
            Result strangerTrip = CabinetService.travel(stranger, level, a);
            check(helper, strangerTrip == Result.TRAVELLED, () -> "a shared pair refused a stranger: " + strangerTrip);

            check(helper, CabinetService.gesture(owner, level, a) == Result.CONFIRM_UNLINK, () -> "unlinking did not ask first");
            check(helper, CabinetPairs.get(level).pairAt(GlobalPos.of(level.dimension(), a)) != null,
                    () -> "the first unlink gesture already unlinked");
            check(helper, CabinetService.gesture(owner, level, a) == Result.UNLINKED, () -> "the confirmed gesture did not unlink");
            check(helper, CabinetPairs.get(level).pairAt(GlobalPos.of(level.dimension(), b)) == null, () -> "B is still linked");
            helper.succeed();
        } finally {
            CabinetPairs.get(level).sever(GlobalPos.of(level.dimension(), a));
            WizardTestSupport.retire(helper, owner);
            WizardTestSupport.retire(helper, stranger);
            artefacts.run();
        }
    }

    private static void breakingSevers(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer owner = player(helper, "CabinetBreaker");
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        try {
            floor(helper);
            place(helper, A, owner);
            place(helper, B, owner);
            CabinetService.gesture(owner, level, a);
            check(helper, CabinetService.gesture(owner, level, helper.absolutePos(B)) == Result.LINKED, () -> "setup: no link");

            // The upper half: the lower one goes with it, and the lower one's leaving is what severs the pair.
            helper.destroyBlock(B.above());
            check(helper, !helper.getBlockState(B).is(ModBlocks.VANISHING_CABINET.get()), () -> "the lower half outlived the upper");
            check(helper, CabinetPairs.get(level).pairAt(GlobalPos.of(level.dimension(), a)) == null,
                    () -> "A is still linked to a cabinet that is gone");
            check(helper, CabinetService.travel(owner, level, a) == Result.NOT_LINKED, () -> "A still leads somewhere");
            helper.succeed();
        } finally {
            CabinetPairs.get(level).sever(GlobalPos.of(level.dimension(), a));
            WizardTestSupport.retire(helper, owner);
            artefacts.run();
        }
    }

    private static void blockedExitRefuses(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer owner = player(helper, "CabinetWalled");
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        try {
            floor(helper);
            place(helper, A, owner);
            place(helper, B, owner);
            CabinetService.gesture(owner, level, a);
            CabinetService.gesture(owner, level, helper.absolutePos(B));
            BlockPos door = B.relative(Direction.NORTH);
            helper.setBlock(door, Blocks.STONE);
            helper.setBlock(door.above(), Blocks.STONE);
            Vec3 before = owner.position();
            Result result = CabinetService.travel(owner, level, a);
            check(helper, result == Result.OBSTRUCTED, () -> "a walled exit gave: " + result);
            check(helper, owner.position().equals(before), () -> "the player moved anyway");
            helper.succeed();
        } finally {
            CabinetPairs.get(level).sever(GlobalPos.of(level.dimension(), a));
            WizardTestSupport.retire(helper, owner);
            artefacts.run();
        }
    }

    // ── setup ───────────────────────────────────────────────────────────────

    /** Stone under both cabinets and the squares in front of them, so an arrival has footing. */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** A cabinet as a player sets one down: both halves, facing north, the placer recorded as its owner. */
    private static void place(GameTestHelper helper, BlockPos lower, ServerPlayer placer) {
        BlockState state = ModBlocks.VANISHING_CABINET.get().defaultBlockState()
                .setValue(VanishingCabinetBlock.FACING, Direction.NORTH);
        helper.setBlock(lower, state.setValue(VanishingCabinetBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(lower.above(), state.setValue(VanishingCabinetBlock.HALF, DoubleBlockHalf.UPPER));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(lower)) instanceof VanishingCabinetBlockEntity be) {
            be.setOwner(placer.getUUID());
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
