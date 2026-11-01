package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorService;
import at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorSword;
import at.koopro.wizardsandbeasts.chamber.ChamberEncounter;
import at.koopro.wizardsandbeasts.chamber.ChamberEvents;
import at.koopro.wizardsandbeasts.chamber.ChamberGateBlock;
import at.koopro.wizardsandbeasts.chamber.ChamberHeartBlockEntity;
import at.koopro.wizardsandbeasts.chamber.ChamberQuestService;
import at.koopro.wizardsandbeasts.chamber.ChamberSealBlock;
import at.koopro.wizardsandbeasts.chamber.ChamberStage;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The Chamber of Secrets on a real server: the seal's two ways open, the basin holding the gate, and the basilisk
 * encounter from waking to the sword. Each scene is built in a few blocks; the encounter scenarios run alone, because
 * a Chamber's hall reaches far past a scenario's own plot and would wake on a neighbour's player.
 */
public final class ChamberTests {

    private ChamberTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("chamber_seal_opens_by_spell_and_by_knocker",
                "chamber: Alohomora's rule (an OPEN property at lock tier 0) opens the whole seal; the knocker button "
                        + "opens it as well and it stays open after the button springs back",
                ChamberTests::sealOpens);
        tests.add("chamber_gate_follows_the_basin",
                "chamber: filling the basin raises the whole gate and credits a player standing by; emptying it "
                        + "lowers the gate again",
                ChamberTests::gateFollowsBasin);
        tests.addAlone("chamber_basilisk_wakes_once_and_gives_the_sword",
                "chamber: entering the hall wakes one persistent basilisk bound to the heart; its death clears the "
                        + "Chamber, credits the player and raises an imbued sword only they can take, once",
                ChamberTests::wakesAndGivesTheSword);
        tests.addAlone("chamber_no_second_sword_while_one_is_out",
                "chamber: a Chamber cleared while the sword is out in the world gives no sword but still credits",
                ChamberTests::noSecondSword);
        tests.addAlone("chamber_empty_hall_puts_the_basilisk_back",
                "chamber: a hall left empty for two minutes puts its basilisk back; the next visitor meets a new one, "
                        + "and never two",
                ChamberTests::emptyHallResets);
        tests.add("chamber_gate_waits_for_someone_in_it",
                "chamber: emptying the basin does not drop the gate on a player standing in it; it falls once they leave",
                ChamberTests::gateWaits);
        tests.add("chamber_hall_is_found_at_every_rotation",
                "chamber: the heart's measured hall covers the hall, and not the landing, at all four rotations a "
                        + "generated Chamber can take",
                ChamberTests::everyRotation);
        tests.add("chamber_quest_survives_reconnect_and_death",
                "chamber: quest stages are saved with the player and come through a reconnect and a death",
                ChamberTests::questSurvives);
        tests.addAlone("chamber_two_players_share_the_fight",
                "chamber: both players see the boss bar and leaving the hall hides it; both are credited; only the last "
                        + "blow's player is given the sword, and the other cannot pick it up",
                ChamberTests::twoPlayers);
        tests.addAlone("chamber_stale_basilisk_is_removed",
                "chamber: a basilisk bound to this heart that is not the one it is waiting on is removed, so a Chamber "
                        + "never holds two",
                ChamberTests::staleBasilisk);
        tests.addAlone("chamber_no_player_blow_goes_to_the_nearest",
                "chamber: a basilisk killed by no player's hand gives the sword to the nearest player credited",
                ChamberTests::noPlayerBlow);
        tests.addAlone("chamber_spell_kill_names_the_caster",
                "chamber: a bolt deals nameless magic damage, yet its caster is noted, is given the sword though another "
                        + "player stands nearer the dais, and the fang drops",
                ChamberTests::spellKill);
        tests.add("chamber_ritual_basilisk_clears_nothing",
                "chamber: a basilisk no Chamber woke answers to no heart",
                ChamberTests::ritualBasiliskClearsNothing);
    }

    private static void sealOpens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos low = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos high = low.above();
        BlockPos wall = high.above();
        level.setBlockAndUpdate(low.below(), Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(low, ModBlocks.CHAMBER_SEAL.get().defaultBlockState());
        level.setBlockAndUpdate(high, ModBlocks.CHAMBER_SEAL.get().defaultBlockState());
        level.setBlockAndUpdate(wall, Blocks.STONE_BRICKS.defaultBlockState());

        BlockState seal = level.getBlockState(low);
        check(helper, seal.hasProperty(BlockStateProperties.OPEN) && SpellCastSupport.lockTier(seal) == 0,
                () -> "Alohomora would not open the seal: OPEN " + seal.hasProperty(BlockStateProperties.OPEN)
                        + ", lock tier " + SpellCastSupport.lockTier(seal));
        // What Alohomora does to such a block (SpellCastTargetedHandler.handleAlohomora).
        level.setBlockAndUpdate(low, seal.cycle(BlockStateProperties.OPEN));
        check(helper, open(level, low) && open(level, high), () -> "Alohomora opened only part of the seal");
        level.setBlockAndUpdate(low, level.getBlockState(low).cycle(BlockStateProperties.OPEN));
        check(helper, !open(level, low) && !open(level, high), () -> "a second Alohomora did not close it");

        BlockPos button = wall.west();
        level.setBlockAndUpdate(button, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.WEST));
        BlockState buttonState = level.getBlockState(button);
        ((ButtonBlock) buttonState.getBlock()).press(buttonState, level, button, null);
        check(helper, open(level, low) && open(level, high), () -> "the knocker did not open the seal");
        helper.runAfterDelay(40, () -> {
            check(helper, !level.getBlockState(button).getValue(ButtonBlock.POWERED), () -> "setup: button still down");
            check(helper, open(level, low) && open(level, high), () -> "the seal shut when the knocker sprang back");
            // A second knock must not shut it on someone standing in the doorway, or wall them in beyond it.
            BlockState again = level.getBlockState(button);
            ((ButtonBlock) again.getBlock()).press(again, level, button, null);
            check(helper, open(level, low) && open(level, high), () -> "a second knock closed the seal");
            helper.succeed();
        });
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChamberSealBlock && state.getValue(ChamberSealBlock.OPEN);
    }

    private static void gateFollowsBasin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer witness = WizardTestSupport.placeMockPlayer(helper, "GateWitness", GameType.CREATIVE);
        WizardTestSupport.parkAtOrigin(helper, witness);
        BlockPos basin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos comparator = basin.south();
        BlockPos gateLow = comparator.south();
        BlockPos gateHigh = gateLow.above();
        for (BlockPos floor : List.of(basin.below(), comparator.below(), gateLow.below())) {
            level.setBlockAndUpdate(floor, Blocks.STONE_BRICKS.defaultBlockState());
        }
        level.setBlockAndUpdate(basin, Blocks.CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(comparator, Blocks.COMPARATOR.defaultBlockState()
                .setValue(ComparatorBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(gateLow, ModBlocks.CHAMBER_GATE.get().defaultBlockState());
        level.setBlockAndUpdate(gateHigh, ModBlocks.CHAMBER_GATE.get().defaultBlockState());
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    check(helper, !raised(level, gateLow), () -> "the gate stood up over a dry basin");
                    level.setBlockAndUpdate(basin, Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, 3));
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    check(helper, raised(level, gateLow) && raised(level, gateHigh),
                            () -> "a full basin did not raise the whole gate");
                    check(helper, ChamberQuestService.quest(witness).has(ChamberStage.GATE_OPENED),
                            () -> "the player standing by was not credited");
                    level.setBlockAndUpdate(basin, Blocks.CAULDRON.defaultBlockState());
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    check(helper, !raised(level, gateLow) && !raised(level, gateHigh),
                            () -> "an emptied basin left the gate up");
                    retire(helper, witness);
                    helper.succeed();
                });
    }

    private static void gateWaits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer inGate = WizardTestSupport.placeMockPlayer(helper, "InTheGate", GameType.SURVIVAL);
        BlockPos basin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos comparator = basin.south();
        BlockPos gateLow = comparator.south();
        BlockPos gateHigh = gateLow.above();
        for (BlockPos floor : List.of(basin.below(), comparator.below(), gateLow.below())) {
            level.setBlockAndUpdate(floor, Blocks.STONE_BRICKS.defaultBlockState());
        }
        level.setBlockAndUpdate(gateLow, ModBlocks.CHAMBER_GATE.get().defaultBlockState());
        level.setBlockAndUpdate(gateHigh, ModBlocks.CHAMBER_GATE.get().defaultBlockState());
        level.setBlockAndUpdate(basin, Blocks.CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(comparator, Blocks.COMPARATOR.defaultBlockState()
                .setValue(ComparatorBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenIdle(4)
                // Filled after the comparator stands, as in play: a comparator reads a change, not what it finds.
                .thenExecute(() -> level.setBlockAndUpdate(basin, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, 3)))
                .thenIdle(6)
                .thenExecute(() -> {
                    check(helper, raised(level, gateLow), () -> "setup: the gate did not rise");
                    inGate.teleportTo(gateLow.getX() + 0.5, gateLow.getY(), gateLow.getZ() + 0.5);
                    inGate.setNoGravity(true);
                    level.setBlockAndUpdate(basin, Blocks.CAULDRON.defaultBlockState());
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    check(helper, raised(level, gateLow) && raised(level, gateHigh),
                            () -> "the gate fell on a player standing in it");
                    inGate.teleportTo(gateLow.getX() + 0.5, gateLow.getY() + 8, gateLow.getZ() + 0.5);
                })
                .thenIdle(ChamberTestTicks.HOLD_RECHECK + 6)
                .thenExecute(() -> {
                    check(helper, !raised(level, gateLow) && !raised(level, gateHigh),
                            () -> "the gate stayed up after the player left it");
                    retire(helper, inGate);
                    helper.succeed();
                });
    }

    /** Waits the gate's re-check; a test can read no package-private constant from here. */
    private static final class ChamberTestTicks {
        static final int HOLD_RECHECK = 20;
    }

    /** A template coordinate after a generated Chamber's rotation about its centre (it is 41 wide, so 20). */
    private static BlockPos rotated(int x, int y, int z, Rotation rotation) {
        int dx = x - 20;
        int dz = z - 20;
        return switch (rotation) {
            case NONE -> new BlockPos(x, y, z);
            case CLOCKWISE_90 -> new BlockPos(20 - dz, y, 20 + dx);
            case CLOCKWISE_180 -> new BlockPos(20 - dx, y, 20 - dz);
            case COUNTERCLOCKWISE_90 -> new BlockPos(20 + dz, y, 20 - dx);
        };
    }

    private static void everyRotation(GameTestHelper helper) {
        for (Rotation rotation : Rotation.values()) {
            Direction facing = rotation.rotate(Direction.NORTH);
            BlockPos heart = rotated(32, 8, 37, rotation);
            AABB hall = ChamberHeartBlockEntity.hall(heart, facing);
            for (int[] c : new int[][] {{26, 7, 15}, {39, 7, 15}, {26, 15, 38}, {39, 15, 38}}) {
                BlockPos corner = rotated(c[0], c[1], c[2], rotation);
                check(helper, hall.contains(corner.getCenter()),
                        () -> rotation + ": the heart does not see the hall's corner " + corner + " in " + hall);
            }
            BlockPos landing = rotated(23, 8, 20, rotation);
            check(helper, !hall.contains(landing.getCenter()), () -> rotation + ": the heart's hall takes in the landing");
            BlockPos passage = rotated(36, 8, 13, rotation);
            check(helper, !hall.contains(passage.getCenter()), () -> rotation + ": the heart's hall takes in the passage");
            var spawn = ChamberHeartBlockEntity.spawnPoint(heart, facing);
            BlockPos expected = rotated(32, 7, 25, rotation);
            check(helper, BlockPos.containing(spawn).equals(expected),
                    () -> rotation + ": the basilisk would wake at " + spawn + ", not " + expected);
        }
        helper.succeed();
    }

    private static void questSurvives(GameTestHelper helper) {
        java.util.UUID id = java.util.UUID.randomUUID();
        ServerPlayer first = WizardTestSupport.placeMockPlayer(helper, "QuestReturns", id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, first);
        ChamberQuestService.reach(first, ChamberStage.SEAL_OPENED);
        helper.getLevel().getServer().getPlayerList().saveAll();
        retire(helper, first);
        ServerPlayer back = WizardTestSupport.placeMockPlayer(helper, "QuestReturns", id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, back);
        check(helper, ChamberQuestService.quest(back).has(ChamberStage.SEAL_OPENED),
                () -> "the stage did not come back from a reconnect: " + ChamberQuestService.quest(back));
        check(helper, !ChamberQuestService.reach(back, ChamberStage.SEAL_OPENED), () -> "a saved stage was reached again");
        helper.startSequence()
                .thenExecute(() -> back.kill(helper.getLevel()))
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerPlayer fresh = helper.getLevel().getServer().getPlayerList()
                            .respawn(back, false, Entity.RemovalReason.KILLED);
                    try {
                        check(helper, ChamberQuestService.quest(fresh).has(ChamberStage.SEAL_OPENED),
                                () -> "the stage did not come through death");
                        helper.succeed();
                    } finally {
                        retire(helper, fresh);
                    }
                });
    }

    private static void twoPlayers(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        ServerPlayer slayer = visitor(helper, "LastBlow", rel);
        ServerPlayer ally = visitor(helper, "AllyInHall", rel);
        GryffindorSword.get(level).forget();
        try {
            heart.look(level, heart.getBlockState());
            LivingEntity basilisk = basilisk(level, heart);
            check(helper, basilisk != null, () -> "setup: the basilisk did not wake");
            check(helper, heart.barViewers().contains(slayer) && heart.barViewers().contains(ally),
                    () -> "not everyone in the hall sees the boss bar: " + heart.barViewers().size());

            basilisk.hurtServer(level, level.damageSources().playerAttack(ally), 5f);
            BlockPos away = helper.absolutePos(rel.north(6));
            ally.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
            heart.look(level, heart.getBlockState());
            check(helper, !heart.barViewers().contains(ally) && heart.barViewers().contains(slayer),
                    () -> "leaving the hall did not hide the boss bar");

            basilisk.hurtServer(level, level.damageSources().playerAttack(slayer), 100_000f);
            check(helper, ChamberQuestService.quest(ally).has(ChamberStage.BASILISK_SLAIN),
                    () -> "a player who fought it and stepped out was not credited");
            check(helper, !ChamberQuestService.quest(ally).has(ChamberStage.SWORD_EARNED)
                            && ChamberQuestService.quest(slayer).has(ChamberStage.SWORD_EARNED),
                    () -> "the sword went to the wrong player");
            check(helper, heart.barViewers().isEmpty(), () -> "the boss bar outlived the basilisk");

            List<ItemEntity> swords = swords(level, heart);
            check(helper, swords.size() == 1, () -> "expected one sword, found " + swords.size());
            ItemEntity sword = swords.getFirst();
            sword.setNoPickUpDelay();
            sword.playerTouch(ally);
            check(helper, sword.isAlive() && ally.getInventory().countItem(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get()) == 0,
                    () -> "the other player picked up the slayer's sword");
            sword.playerTouch(slayer);
            check(helper, slayer.getInventory().countItem(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get()) == 1,
                    () -> "the slayer could not pick up the sword");
            check(helper, GryffindorSword.get(level).present() != null, () -> "picking it up forgot the world's sword");
            helper.succeed();
        } finally {
            swords(level, heart).forEach(Entity::discard);
            basilisks(level, heart).forEach(Entity::discard);
            GryffindorSword.get(level).forget();
            retire(helper, slayer);
            retire(helper, ally);
            chamber.run();
            artefacts.run();
        }
    }

    private static void staleBasilisk(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        BlockPos spot = helper.absolutePos(rel.south(8)).below();
        Entity stale = ModCreatures.ENTITIES.get("basilisk").get().create(level, EntitySpawnReason.COMMAND);
        check(helper, stale != null, () -> "setup: no basilisk");
        stale.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
        stale.setData(ModAttachments.CHAMBER_HEART.get(), heart.getBlockPos().asLong());
        level.addFreshEntity(stale);
        ServerPlayer visitor = null;
        try {
            heart.look(level, heart.getBlockState());
            check(helper, stale.isRemoved(), () -> "a sleeping heart left a stale basilisk in its hall");
            visitor = visitor(helper, "StaleWitness", rel);
            heart.look(level, heart.getBlockState());
            check(helper, basilisks(level, heart).size() == 1, () -> "two basilisks in one Chamber");
            helper.succeed();
        } finally {
            basilisks(level, heart).forEach(Entity::discard);
            if (!stale.isRemoved()) {
                stale.discard();
            }
            retire(helper, visitor);
            chamber.run();
        }
    }

    /** The real bolt: {@code onHitEntity} is protected, and flight is not what is being tested. */
    private static final class Bolt extends at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity {
        Bolt(ServerLevel level, LivingEntity caster, String spell) {
            super(level, caster, spell);
        }

        void strike(Entity target) {
            onHitEntity(new net.minecraft.world.phys.EntityHitResult(target));
        }
    }

    private static void spellKill(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        ServerPlayer caster = visitor(helper, "FarCaster", rel.south(10));
        ServerPlayer nearDais = visitor(helper, "NearTheDais", rel);
        GryffindorSword.get(level).forget();
        try {
            heart.look(level, heart.getBlockState());
            LivingEntity basilisk = basilisk(level, heart);
            check(helper, basilisk != null, () -> "setup: the basilisk did not wake");
            basilisk.setHealth(0.5f);
            new Bolt(level, caster, "wizards_and_beasts:stupefy").strike(basilisk);
            check(helper, heart.phase() == ChamberEncounter.Phase.SLAIN,
                    () -> "the bolt did not kill it: " + basilisk.getHealth());
            List<ItemEntity> swords = swords(level, heart);
            check(helper, swords.size() == 1 && caster.getUUID().equals(swords.getFirst().getTarget()),
                    () -> "the sword did not go to the caster who struck the last blow");
            var fangs = level.getEntitiesOfClass(ItemEntity.class, basilisk.getBoundingBox().inflate(4),
                    e -> e.getItem().getItem() == net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", "basilisk_fang")));
            check(helper, !fangs.isEmpty(), () -> "a spell kill dropped no fang: killed_by_player did not see the caster");
            helper.succeed();
        } finally {
            swords(level, heart).forEach(Entity::discard);
            basilisks(level, heart).forEach(Entity::discard);
            GryffindorSword.get(level).forget();
            retire(helper, caster);
            retire(helper, nearDais);
            chamber.run();
            artefacts.run();
        }
    }

    private static void noPlayerBlow(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        ServerPlayer witness = visitor(helper, "StoodByTheDais", rel);
        GryffindorSword.get(level).forget();
        try {
            heart.look(level, heart.getBlockState());
            LivingEntity basilisk = basilisk(level, heart);
            check(helper, basilisk != null, () -> "setup: the basilisk did not wake");
            basilisk.hurtServer(level, level.damageSources().generic(), 100_000f);
            check(helper, heart.phase() == ChamberEncounter.Phase.SLAIN, () -> "a death with no player did not clear");
            List<ItemEntity> swords = swords(level, heart);
            check(helper, swords.size() == 1 && witness.getUUID().equals(swords.getFirst().getTarget()),
                    () -> "the sword did not go to the nearest credited player: " + swords);
            helper.succeed();
        } finally {
            swords(level, heart).forEach(Entity::discard);
            basilisks(level, heart).forEach(Entity::discard);
            GryffindorSword.get(level).forget();
            retire(helper, witness);
            chamber.run();
            artefacts.run();
        }
    }

    private static boolean raised(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChamberGateBlock && state.getValue(ChamberGateBlock.POWERED);
    }

    // ── the encounter ─────────────────────────────────────────────────────

    /** A heart facing south (the hall runs +z), a floor under the hall, and the heart's block entity. */
    private static ChamberHeartBlockEntity heart(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        AABB hall = ChamberHeartBlockEntity.hall(pos, Direction.SOUTH);
        for (int x = (int) hall.minX; x < hall.maxX; x++) {
            for (int z = (int) hall.minZ; z < hall.maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(x, pos.getY() - 2, z), Blocks.STONE.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(pos, ModBlocks.CHAMBER_HEART.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        WizardTestSupport.forceChunks(helper, new BlockPos(-8, 0, -2), new BlockPos(8, 0, 24));
        return (ChamberHeartBlockEntity) level.getBlockEntity(pos);
    }

    private static ServerPlayer visitor(GameTestHelper helper, String name, BlockPos heartRelative) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.CREATIVE);
        BlockPos at = helper.absolutePos(heartRelative.south(5));
        player.teleportTo(at.getX() + 0.5, at.getY() - 1, at.getZ() + 0.5);
        player.setNoGravity(true);
        return player;
    }

    private static void wakesAndGivesTheSword(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        ServerPlayer slayer = visitor(helper, "BasiliskSlayer", rel);
        GryffindorSword.get(level).forget();
        try {
            heart.look(level, heart.getBlockState());
            LivingEntity basilisk = basilisk(level, heart);
            check(helper, heart.phase() == ChamberEncounter.Phase.AWAKE && basilisk != null,
                    () -> "a player in the hall did not wake the basilisk: " + heart.phase());
            check(helper, basilisk instanceof Mob mob && mob.isPersistenceRequired(),
                    () -> "the Chamber's basilisk can despawn");
            check(helper, ChamberEvents.heartOf(level, basilisk) == heart, () -> "the basilisk is not bound to its heart");
            check(helper, ChamberQuestService.quest(slayer).has(ChamberStage.HALL_ENTERED),
                    () -> "standing in the hall was not recorded");

            heart.look(level, heart.getBlockState());
            check(helper, basilisks(level, heart).size() == 1, () -> "a second look woke a second basilisk");

            basilisk.hurtServer(level, level.damageSources().playerAttack(slayer), 100_000f);
            check(helper, heart.phase() == ChamberEncounter.Phase.SLAIN, () -> "the death did not clear the Chamber");
            check(helper, ChamberQuestService.quest(slayer).has(ChamberStage.BASILISK_SLAIN),
                    () -> "the slayer was not credited");
            List<ItemEntity> swords = swords(level, heart);
            check(helper, swords.size() == 1, () -> "expected one sword over the dais, found " + swords.size());
            ItemEntity sword = swords.getFirst();
            check(helper, Boolean.TRUE.equals(sword.getItem().get(ModDataComponents.VENOM_IMBUED.get())),
                    () -> "the Chamber's sword is not imbued");
            check(helper, sword.getTarget() != null && sword.getTarget().equals(slayer.getUUID()),
                    () -> "the sword can be taken by someone other than its slayer");
            check(helper, ChamberQuestService.quest(slayer).has(ChamberStage.SWORD_EARNED),
                    () -> "the slayer's quest does not record the sword");

            heart.look(level, heart.getBlockState());
            check(helper, basilisks(level, heart).isEmpty() && heart.phase() == ChamberEncounter.Phase.SLAIN,
                    () -> "a cleared Chamber woke another basilisk");
            helper.succeed();
        } finally {
            swords(level, heart).forEach(Entity::discard);
            basilisks(level, heart).forEach(Entity::discard);
            GryffindorSword.get(level).forget();
            retire(helper, slayer);
            chamber.run();
            artefacts.run();
        }
    }

    private static void noSecondSword(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        ServerPlayer slayer = visitor(helper, "SecondSlayer", rel);
        ServerPlayer holder = WizardTestSupport.placeMockPlayer(helper, "SwordHolder", GameType.CREATIVE);
        GryffindorSword.get(level).forget();
        ItemEntity existing = null;
        try {
            existing = GryffindorService.present(holder);
            heart.look(level, heart.getBlockState());
            LivingEntity basilisk = basilisk(level, heart);
            check(helper, basilisk != null, () -> "setup: the basilisk did not wake");
            basilisk.hurtServer(level, level.damageSources().playerAttack(slayer), 100_000f);
            check(helper, swords(level, heart).isEmpty(), () -> "a second sword rose while the first was out");
            check(helper, ChamberQuestService.quest(slayer).has(ChamberStage.BASILISK_SLAIN)
                            && !ChamberQuestService.quest(slayer).has(ChamberStage.SWORD_EARNED),
                    () -> "the slayer's record is wrong: " + ChamberQuestService.quest(slayer));
            helper.succeed();
        } finally {
            if (existing != null) {
                existing.discard();
            }
            swords(level, heart).forEach(Entity::discard);
            basilisks(level, heart).forEach(Entity::discard);
            GryffindorSword.get(level).forget();
            retire(helper, slayer);
            retire(helper, holder);
            chamber.run();
            artefacts.run();
        }
    }

    private static void emptyHallResets(GameTestHelper helper) {
        Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 3, 0);
        ChamberHeartBlockEntity heart = heart(helper, rel);
        ServerPlayer visitor = visitor(helper, "HallVisitor", rel);
        try {
            heart.look(level, heart.getBlockState());
            LivingEntity first = basilisk(level, heart);
            check(helper, first != null, () -> "setup: the basilisk did not wake");
            BlockPos away = helper.absolutePos(rel.north(6));
            visitor.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
            for (int i = 0; i * 20 <= ChamberEncounter.RESET_TICKS; i++) {
                heart.look(level, heart.getBlockState());
            }
            check(helper, heart.phase() == ChamberEncounter.Phase.SLEEPING && first != null && first.isRemoved(),
                    () -> "an empty hall did not put the basilisk back: " + heart.phase());
            BlockPos back = helper.absolutePos(rel.south(5));
            visitor.teleportTo(back.getX() + 0.5, back.getY() - 1, back.getZ() + 0.5);
            heart.look(level, heart.getBlockState());
            LivingEntity second = basilisk(level, heart);
            check(helper, second != null && first != null && !second.getUUID().equals(first.getUUID()),
                    () -> "the returning visitor met no basilisk, or the old one");
            check(helper, basilisks(level, heart).size() == 1, () -> "two basilisks in one Chamber");
            helper.succeed();
        } finally {
            basilisks(level, heart).forEach(Entity::discard);
            retire(helper, visitor);
            chamber.run();
        }
    }

    private static void ritualBasiliskClearsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity plain = ModCreatures.ENTITIES.get("basilisk").get().create(level, EntitySpawnReason.COMMAND);
        check(helper, plain != null, () -> "setup: no basilisk");
        check(helper, ChamberEvents.heartOf(level, plain) == null, () -> "an unbound basilisk found a heart");
        check(helper, !plain.hasData(ModAttachments.CHAMBER_HEART.get()), () -> "a new basilisk is bound already");
        helper.succeed();
    }

    private static LivingEntity basilisk(ServerLevel level, ChamberHeartBlockEntity heart) {
        return heart.basilisk() != null && level.getEntity(heart.basilisk()) instanceof LivingEntity living
                ? living : null;
    }

    private static List<LivingEntity> basilisks(ServerLevel level, ChamberHeartBlockEntity heart) {
        AABB area = ChamberHeartBlockEntity.hall(heart.getBlockPos(), Direction.SOUTH).inflate(16);
        return level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e.hasData(ModAttachments.CHAMBER_HEART.get()));
    }

    private static List<ItemEntity> swords(ServerLevel level, ChamberHeartBlockEntity heart) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(heart.getBlockPos()).inflate(4),
                e -> e.getItem().is(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get()));
    }
}
