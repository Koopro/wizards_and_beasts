package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.beast.BowtruckleEntity;
import at.koopro.wizardsandbeasts.entity.beast.CornishPixieEntity;
import at.koopro.wizardsandbeasts.entity.beast.MooncalfEntity;
import at.koopro.wizardsandbeasts.entity.beast.HidebehindEntity;
import at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.NifflerEventHandler;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.List;
import at.koopro.wizardsandbeasts.entity.beast.RunespoorEntity;
import at.koopro.wizardsandbeasts.entity.beast.ai.HidebehindStalkGoal;
import at.koopro.wizardsandbeasts.entity.creature.DragonEntity;
import at.koopro.wizardsandbeasts.entity.creature.HippogriffEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.DragonBreathGoal;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.entity.PartEntity;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Regressions for the Beast Renaissance correctness pass (BR-0, {@code documentation/BEAST_RENAISSANCE.md} §2.3).
 * Each scenario names the defect it pins. Rules that need no world are unit tests; these are the claims only a
 * running server can answer.
 */
public final class BeastRenaissanceTests {

    private BeastRenaissanceTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("br0_winged_walker_loaded_weightless_comes_down",
                "D5: a hippogriff that loads weightless but not flying gets its gravity back",
                BeastRenaissanceTests::wingedWalkerLoadedWeightlessComesDown);
        tests.add("br0_fliers_take_no_fall_damage",
                "D7: dragons and other generic fliers are not hurt by the drop between flight targets",
                BeastRenaissanceTests::fliersTakeNoFallDamage);
        tests.add("br0_runespoor_heads_follow_the_body_id",
                "D4: the runespoor's head ids follow its own, so a client's click on a head reaches the server",
                BeastRenaissanceTests::runespoorHeadsFollowTheBodyId);
        tests.addAlone("br0_hidebehind_strikes_a_turned_back",
                "D3: the hidebehind stops stalking and lets its attack run once it is behind a turned back",
                BeastRenaissanceTests::hidebehindStrikesATurnedBack);
        tests.add("br0_streeler_floats",
                "D14: the streeler floats and avoids water instead of walking in and drowning",
                BeastRenaissanceTests::streelerFloats);
        tests.add("br0_niffler_sneak_click_carries_and_logout_sets_it_down",
                "D6, D9: an owner's sneak + empty hand pockets a trusting niffler; leaving the server sets it down",
                BeastRenaissanceTests::nifflerSneakClickCarriesAndLogoutSetsItDown);
        tests.add("br0_bowtruckle_sneak_click_picks_a_lock",
                "D6: an owner's sneak + empty hand sets a trusting bowtruckle to the nearby lock",
                BeastRenaissanceTests::bowtruckleSneakClickPicksALock);
        tests.addAlone("br0_dragon_breath_respects_mob_griefing",
                "D8: dragon fire scorches the ground only where mobs may grief",
                BeastRenaissanceTests::dragonBreathRespectsMobGriefing);
        tests.add("br0_pixie_never_drops_into_lava",
                "D12: a pixie's stolen item lands on ground, never into lava",
                BeastRenaissanceTests::pixieNeverDropsIntoLava);
        tests.add("br0_burrowed_mooncalf_cannot_be_struck",
                "D10: a burrowed mooncalf is underground: not pickable, not attackable, not hurt",
                BeastRenaissanceTests::burrowedMooncalfCannotBeStruck);
        tests.add("br0_saved_creature_takes_its_definition_stats",
                "D21: a reloaded creature takes its definition's stats, not the ones saved with it",
                BeastRenaissanceTests::savedCreatureTakesItsDefinitionStats);
        tests.add("br0_grown_niffler_keeps_its_name_and_brings_no_litter",
                "D9: a baby niffler grows into the same animal: name, health share, and no fresh litter",
                BeastRenaissanceTests::grownNifflerKeepsItsNameAndBringsNoLitter);
    }

    private static void nifflerSneakClickCarriesAndLogoutSetsItDown(GameTestHelper helper) {
        NifflerEntity niffler = helper.spawn(ModEntities.NIFFLER.get(), new BlockPos(1, 2, 1));
        niffler.setNoAi(true);
        // A niffler new to the world brings a litter three times in ten. Those babies land outside this test's box
        // and are prey to the next scenario's predators, so they go at once.
        discardLitter(helper, niffler);
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "wandb-br0-niffler", GameType.SURVIVAL);
        owner.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 2.5)));
        niffler.bondState().setOwner(owner.getUUID());

        // Not yet trusting enough to be carried: the sneak-click is the shared pat, and nothing is pocketed.
        niffler.setSyncedBondLevel(niffler.bondState().setLevel(40, 100));
        owner.setShiftKeyDown(true);
        niffler.interact(owner, InteractionHand.MAIN_HAND);
        check(helper, !niffler.isCarried(), () -> "a niffler at bond 40 was pocketed");

        // Trusting: the same sneak-click pockets it. It used to be swallowed by the pat and never reached the niffler.
        niffler.setSyncedBondLevel(niffler.bondState().setLevel(80, 100));
        niffler.interact(owner, InteractionHand.MAIN_HAND);
        check(helper, niffler.isCarried(), () -> "an owner's sneak + empty hand did not pocket a niffler at bond 80");
        check(helper, owner.getData(ModAttachments.CARRIED_NIFFLER.get()).isCarrying(),
                () -> "the owner's pocket does not name the niffler they carry");

        // Leaving the server while carrying it: it is set down, and the pocket is emptied with it.
        NifflerEventHandler.onOwnerLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
        check(helper, !niffler.isCarried(), () -> "the owner left and the niffler stayed 'carried' by nobody");
        check(helper, !owner.getData(ModAttachments.CARRIED_NIFFLER.get()).isCarrying(),
                () -> "the owner left with a pocket still naming the niffler");

        owner.setShiftKeyDown(false);
        retire(helper, owner);
        niffler.discard();
        helper.succeed();
    }

    private static void bowtruckleSneakClickPicksALock(GameTestHelper helper) {
        BlockPos lock = new BlockPos(3, 2, 1);
        helper.setBlock(lock.below(), Blocks.STONE);
        helper.setBlock(lock, Blocks.IRON_TRAPDOOR);
        BowtruckleEntity bowtruckle = helper.spawn(ModEntities.BOWTRUCKLE.get(), new BlockPos(1, 2, 1));
        bowtruckle.setNoAi(true);
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "wandb-br0-bowtruckle", GameType.SURVIVAL);
        owner.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 2.5)));
        bowtruckle.bondState().setOwner(owner.getUUID());
        bowtruckle.setSyncedBondLevel(bowtruckle.bondState().setLevel(BowtruckleEntity.LOCKPICK_BOND, 100));

        owner.setShiftKeyDown(true);
        bowtruckle.interact(owner, InteractionHand.MAIN_HAND);
        check(helper, helper.absolutePos(lock).equals(bowtruckle.lockJob()),
                () -> "an owner's sneak + empty hand did not set the bowtruckle to the trapdoor (job: "
                        + bowtruckle.lockJob() + ")");

        owner.setShiftKeyDown(false);
        retire(helper, owner);
        bowtruckle.discard();
        helper.succeed();
    }

    private static void dragonBreathRespectsMobGriefing(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 0, 0);
        BlockPos max = new BlockPos(12, 6, 12);
        WizardTestSupport.forceChunks(helper, min, max);
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        DragonEntity dragon = (DragonEntity) helper.spawn(type("common_welsh_green"), new BlockPos(2, 1, 6));
        dragon.setNoAi(true);
        dragon.setNoGravity(true);
        // Facing +x and down onto the stone floor.
        dragon.setYRot(-90.0f);
        dragon.setYHeadRot(-90.0f);
        dragon.setXRot(40.0f);
        DragonBreathGoal breath = new DragonBreathGoal(dragon);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        boolean griefing = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
        try {
            level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, false, level.getServer());
            breath.breathe();
            int withGriefingOff = fires(helper, min, max);
            check(helper, withGriefingOff == 0,
                    () -> "with mobGriefing off a dragon's breath still set " + withGriefingOff + " fires");
            level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, true, level.getServer());
            breath.breathe();
            int withGriefingOn = fires(helper, min, max);
            check(helper, withGriefingOn > 0, () -> "with mobGriefing on a dragon's breath set no fire on the stone");
        } finally {
            level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, griefing, level.getServer());
            for (int x = 0; x <= 12; x++) {
                for (int y = 1; y <= 6; y++) {
                    for (int z = 0; z <= 12; z++) {
                        if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.FIRE)) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                        }
                    }
                }
            }
            dragon.discard();
        }
        helper.succeed();
    }

    private static void pixieNeverDropsIntoLava(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.LAVA);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        check(helper, CornishPixieEntity.safeToDrop(level, helper.absolutePos(new BlockPos(1, 4, 1))),
                () -> "a drop over stone was judged unsafe");
        check(helper, !CornishPixieEntity.safeToDrop(level, helper.absolutePos(new BlockPos(3, 4, 1))),
                () -> "a drop over lava was judged safe; a stolen wand would burn");
        helper.succeed();
    }

    private static void burrowedMooncalfCannotBeStruck(GameTestHelper helper) {
        MooncalfEntity mooncalf = helper.spawn(ModEntities.MOONCALF.get(), new BlockPos(1, 2, 1));
        mooncalf.setNoAi(true);
        if (!mooncalf.isBurrowed()) {
            // A full moon tonight: the claim is about a burrowed mooncalf, so there is nothing to check.
            mooncalf.discard();
            helper.succeed();
            return;
        }
        float before = mooncalf.getHealth();
        boolean hurt = mooncalf.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0f);
        check(helper, !hurt && mooncalf.getHealth() == before, () -> "a burrowed mooncalf was hurt on the surface");
        check(helper, !mooncalf.isPickable() && !mooncalf.isAttackable(),
                () -> "a burrowed mooncalf can still be clicked or targeted");
        mooncalf.discard();
        helper.succeed();
    }

    private static void savedCreatureTakesItsDefinitionStats(GameTestHelper helper) {
        Mob unicorn = (Mob) helper.spawn(type("unicorn"), new BlockPos(1, 2, 1));
        unicorn.setNoAi(true);
        double defined = unicorn.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        // An older save: different stats from a datapack since retuned.
        unicorn.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(defined * 3);
        unicorn.setHealth((float) (defined * 3) * 0.5f);
        Mob loaded = CreatureWildlifeTests.roundTrip(helper, unicorn);
        check(helper, loaded != null, () -> "the unicorn did not load back");
        double after = loaded.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        check(helper, after == defined, () -> "a reloaded unicorn kept the saved max health " + after + ", not " + defined);
        float share = loaded.getHealth() / loaded.getMaxHealth();
        check(helper, Math.abs(share - 0.5f) < 0.02f, () -> "a half-hurt unicorn reloaded at " + share + " of its health");
        unicorn.discard();
        loaded.discard();
        helper.succeed();
    }

    private static int fires(GameTestHelper helper, BlockPos min, BlockPos max) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (helper.getBlockState(pos).is(Blocks.FIRE)) {
                count++;
            }
        }
        return count;
    }

    private static void grownNifflerKeepsItsNameAndBringsNoLitter(GameTestHelper helper) {
        BabyNifflerEntity baby = helper.spawn(ModEntities.BABY_NIFFLER.get(), new BlockPos(2, 2, 2));
        baby.setNoAi(true);
        baby.setCustomName(net.minecraft.network.chat.Component.literal("Teaspoon"));
        baby.setHealth(baby.getMaxHealth() * 0.5f);
        baby.growIntoAdult();

        List<NifflerEntity> adults = helper.getLevel().getEntitiesOfClass(NifflerEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(4),
                niffler -> !niffler.isBaby());
        check(helper, adults.size() == 1, () -> adults.size() + " adults near a baby that grew once; a litter came too");
        NifflerEntity adult = adults.get(0);
        check(helper, adult.hasCustomName() && "Teaspoon".equals(adult.getCustomName().getString()),
                () -> "the grown niffler lost its name");
        float share = adult.getHealth() / adult.getMaxHealth();
        check(helper, Math.abs(share - 0.5f) < 0.01f, () -> "a half-hurt baby grew into an adult at " + share);
        adult.discard();
        helper.succeed();
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void wingedWalkerLoadedWeightlessComesDown(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 0, 0);
        BlockPos max = new BlockPos(4, 6, 4);
        WizardTestSupport.forceChunks(helper, min, max);
        HippogriffEntity[] hippogriff = new HippogriffEntity[1];
        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> {
                    for (int x = 0; x <= 4; x++) {
                        for (int z = 0; z <= 4; z++) {
                            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                        }
                    }
                    hippogriff[0] = helper.spawn(creature("hippogriff", HippogriffEntity.class), new BlockPos(2, 4, 2));
                    // What a reload leaves behind: vanilla saved NoGravity, the creature's own flying flag was not.
                    hippogriff[0].setFlying(false);
                    hippogriff[0].setNoGravity(true);
                })
                .thenWaitUntil(() -> check(helper, !hippogriff[0].isNoGravity(),
                        () -> "a hippogriff that is not flying stayed weightless; it would hang in the air forever"))
                .thenExecute(() -> hippogriff[0].discard())
                .thenSucceed();
    }

    private static void fliersTakeNoFallDamage(GameTestHelper helper) {
        for (String id : new String[] {"common_welsh_green", "hungarian_horntail", "griffin", "abraxan"}) {
            Mob flier = (Mob) helper.spawn(type(id), new BlockPos(1, 2, 1));
            flier.setNoAi(true);
            float before = flier.getHealth();
            boolean hurt = flier.causeFallDamage(20.0, 1.0f, helper.getLevel().damageSources().fall());
            check(helper, !hurt && flier.getHealth() == before,
                    () -> id + " took fall damage (" + before + " -> " + flier.getHealth() + ")");
            flier.discard();
        }
        helper.succeed();
    }

    private static void runespoorHeadsFollowTheBodyId(GameTestHelper helper) {
        RunespoorEntity runespoor = helper.spawn(ModEntities.RUNESPOOR.get(), new BlockPos(1, 2, 1));
        runespoor.setNoAi(true);
        PartEntity<?>[] heads = runespoor.getParts();
        check(helper, heads.length == 3, () -> "a runespoor has " + heads.length + " heads");
        for (int i = 0; i < heads.length; i++) {
            int expected = runespoor.getId() + i + 1;
            int actual = heads[i].getId();
            check(helper, actual == expected, () -> "head id " + actual + ", expected " + expected
                    + ": a client and the server would disagree on which entity a head is");
        }
        // A client gets the body's id from the spawn packet and sets it the same way; so must every later id.
        runespoor.setId(runespoor.getId() + 100);
        check(helper, heads[0].getId() == runespoor.getId() + 1, () -> "re-assigning the body's id left the heads behind");
        runespoor.discard();
        helper.succeed();
    }

    private static void hidebehindStrikesATurnedBack(GameTestHelper helper) {
        HidebehindEntity hidebehind = helper.spawn(ModEntities.HIDEBEHIND.get(), new BlockPos(2, 2, 4));
        hidebehind.setNoAi(true);
        hidebehind.setNoGravity(true);
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "wandb-br0-hidebehind", GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        hidebehind.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 4.5)));

        // Looking away (north, -z): the hidebehind 2 blocks behind it may strike, so stalking lets go.
        face(player, 180.0f);
        check(helper, !new HidebehindStalkGoal(hidebehind).canUse(),
                () -> "behind a turned back and within reach, the hidebehind kept stalking; its attack could never run");

        // Turned round to face it (south, +z): it slips away to stalk again.
        face(player, 0.0f);
        check(helper, new HidebehindStalkGoal(hidebehind).canUse(),
                () -> "a player facing the hidebehind did not send it back to stalking");

        retire(helper, player);
        hidebehind.discard();
        helper.succeed();
    }

    private static void streelerFloats(GameTestHelper helper) {
        Entity streeler = helper.spawn(ModEntities.STREELER.get(), new BlockPos(1, 2, 1));
        boolean floats = ((Mob) streeler).goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof FloatGoal);
        check(helper, floats, () -> "the streeler has no float goal, so water drowns it");
        streeler.discard();
        helper.succeed();
    }

    // -- helpers -----------------------------------------------------------------------------------

    private static void discardLitter(GameTestHelper helper, Entity around) {
        helper.getLevel().getEntitiesOfClass(BabyNifflerEntity.class, around.getBoundingBox().inflate(6))
                .forEach(Entity::discard);
    }

    private static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0.0f);
    }

    private static EntityType<?> type(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> EntityType<E> creature(String id, Class<E> type) {
        return (EntityType<E>) type(id);
    }
}
