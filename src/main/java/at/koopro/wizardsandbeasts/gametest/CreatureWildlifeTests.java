package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.EncounterRule;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.creature.ability.MoonBound;
import at.koopro.wizardsandbeasts.creature.wildlife.CreatureSign;
import at.koopro.wizardsandbeasts.creature.wildlife.LycanthropyInfection;
import at.koopro.wizardsandbeasts.entity.beast.BowtruckleEntity;
import at.koopro.wizardsandbeasts.entity.beast.MooncalfEntity;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerSeekShinyItemGoal;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.NifflerEventHandler;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.function.Supplier;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The creatures as wildlife, in a live server: a bestiary that fills by watching, a unicorn that lets only the right
 * person near, a demiguise that sees you coming, a mooncalf herd under the moon, a phoenix that will not stay dead, a
 * bowtruckle that defends its tree, a niffler that wants gold, and a werewolf whose bite carries the curse.
 *
 * <p>Test players are never ticked, so the bestiary scan a real player gets once a second is called directly, and the
 * full moon — which is world time and shared by every scenario — is given to the code that reads it rather than set.
 * Creatures are placed with no AI where movement is not what is being tested, so they stay inside the forced chunks.
 *
 * <p>Scenarios sit high above the grid, between the Ministry scenarios, and further from each other and from them than
 * any radius they read.
 */
public final class CreatureWildlifeTests {

    private static final int BESTIARY_Y = 112;
    private static final int NIFFLER_Y = 137;
    private static final int WEREWOLF_Y = 162;
    private static final int UNICORN_Y = 187;
    private static final int DEMIGUISE_Y = 225;
    private static final int MOONCALF_Y = 275;
    private static final int PHOENIX_Y = 325;
    private static final int BOWTRUCKLE_Y = 362;

    private CreatureWildlifeTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("creature_bestiary_fills_by_watching_not_killing",
                "bestiary: watching a creature deepens its page; killing one only meets it",
                CreatureWildlifeTests::bestiaryFillsByWatching);
        tests.add("creature_unicorn_lets_only_the_right_person_near",
                "unicorn: refuses strangers, is groomed by a quiet watcher, curses its killer and drops nothing",
                CreatureWildlifeTests::unicornLetsOnlyTheRightPersonNear);
        tests.add("creature_demiguise_sees_the_obvious_coming",
                "demiguise: invisible when looked at, sidesteps a straight approach, caught unforeseen, sheds",
                CreatureWildlifeTests::demiguiseSeesTheObviousComing);
        tests.add("creature_mooncalf_dances_under_the_full_moon",
                "mooncalf: burrowed until the full moon, dances, leaves dung at dawn; a kept one stays out",
                CreatureWildlifeTests::mooncalfDancesUnderTheFullMoon);
        tests.add("creature_phoenix_rises_from_its_ashes",
                "phoenix: reborn instead of killed, loyal to who defends it, weeps for them, survives a save",
                CreatureWildlifeTests::phoenixRisesFromItsAshes);
        tests.add("creature_bowtruckle_defends_its_tree",
                "bowtruckle: finds a home tree, turns on whoever cuts it, spares its bonded wandmaker",
                CreatureWildlifeTests::bowtruckleDefendsItsTree);
        tests.add("creature_niffler_wants_gold_most",
                "niffler: goes for gold before nearer copper; a saved niffler brings no new litter on load",
                CreatureWildlifeTests::nifflerWantsGoldMost);
        tests.add("creature_werewolf_bite_carries_the_curse",
                "werewolf: leaves at dawn; its bite under a full moon curses a human, keeping who they are",
                CreatureWildlifeTests::werewolfBiteCarriesTheCurse);
    }

    // ── the bestiary ──────────────────────────────────────────────────────────────────────────────

    private static void bestiaryFillsByWatching(GameTestHelper helper) {
        stage(helper, BESTIARY_Y);
        ServerPlayer naturalist = watcher(helper, "wandb-creature-naturalist", BESTIARY_Y, 2, 0);
        GenericBeastEntity horklump = still(helper, "horklump", BESTIARY_Y, 2, 5);
        GenericBeastEntity ghoul = still(helper, "ghoul", BESTIARY_Y, 5, 5);
        Identifier horklumpPage = page("horklump");
        Identifier ghoulPage = page("ghoul");

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BESTIARY_Y), max(BESTIARY_Y)))
                .thenExecute(() -> {
                    ghoul.setInvisible(true);
                    BestiaryDiscoveryHandler.scan(naturalist);
                    check(helper, tier(naturalist, horklumpPage) == DiscoveryTier.ENCOUNTERED,
                            () -> "a creature in plain sight was not encountered: " + tier(naturalist, horklumpPage));
                    check(helper, tier(naturalist, ghoulPage) == DiscoveryTier.UNKNOWN,
                            () -> "an invisible creature was seen: " + tier(naturalist, ghoulPage));

                    int scans = EncounterRule.OBSERVED_TICKS / 20;
                    for (int i = 0; i < scans; i++) {
                        BestiaryDiscoveryHandler.scan(naturalist);
                    }
                    check(helper, tier(naturalist, horklumpPage) == DiscoveryTier.OBSERVED,
                            () -> "thirty seconds of calm watching did not make an observation: "
                                    + tier(naturalist, horklumpPage) + " after "
                                    + BestiaryDataHelper.getObservedTicks(naturalist, horklumpPage) + " ticks");

                    // Hurting it stops the clock.
                    int before = BestiaryDataHelper.getObservedTicks(naturalist, horklumpPage);
                    horklump.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(naturalist), 0.5f);
                    BestiaryDiscoveryHandler.scan(naturalist);
                    check(helper, BestiaryDataHelper.getObservedTicks(naturalist, horklumpPage) == before,
                            () -> "watching a creature you just hurt still counted as calm");

                    // Killing the ghoul is meeting it, nothing more.
                    ghoul.setInvisible(false);
                    ghoul.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(naturalist), 1000f);
                    check(helper, !ghoul.isAlive(), () -> "the ghoul survived the test blow");
                    check(helper, tier(naturalist, ghoulPage) == DiscoveryTier.ENCOUNTERED,
                            () -> "killing a creature taught more than that it can die: " + tier(naturalist, ghoulPage));
                })
                .thenExecute(() -> {
                    horklump.discard();
                    retire(helper, naturalist);
                })
                .thenSucceed();
    }

    // ── the unicorn ───────────────────────────────────────────────────────────────────────────────

    private static void unicornLetsOnlyTheRightPersonNear(GameTestHelper helper) {
        stage(helper, UNICORN_Y);
        ServerPlayer stranger = watcher(helper, "wandb-creature-stranger", UNICORN_Y, 1, 0);
        ServerPlayer friend = watcher(helper, "wandb-creature-friend", UNICORN_Y, 4, 0);
        GenericBeastEntity unicorn = still(helper, "unicorn", UNICORN_Y, 2, 4);
        GenericBeastEntity[] second = new GenericBeastEntity[1];
        Identifier unicornPage = page("unicorn");
        Item hair = item("unicorn_hair");

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(UNICORN_Y), max(UNICORN_Y)))
                .thenExecute(() -> {
                    stranger.setShiftKeyDown(true);
                    unicorn.interact(stranger, InteractionHand.MAIN_HAND);
                    check(helper, count(stranger, hair) == 0,
                            () -> "a unicorn let a stranger it had never watched comb its hair");

                    BestiaryDataHelper.setTier(friend, unicornPage, DiscoveryTier.OBSERVED);
                    friend.setShiftKeyDown(false);
                    unicorn.interact(friend, InteractionHand.MAIN_HAND);
                    check(helper, count(friend, hair) == 0, () -> "a unicorn let someone stride up to it");

                    friend.setShiftKeyDown(true);
                    unicorn.interact(friend, InteractionHand.MAIN_HAND);
                    check(helper, count(friend, hair) == 1,
                            () -> "a quiet, empty-handed watcher was refused: " + count(friend, hair) + " hair");
                    check(helper, tier(friend, unicornPage) == DiscoveryTier.KNOWN,
                            () -> "being allowed to touch a unicorn did not complete its page: " + tier(friend, unicornPage));
                    unicorn.interact(friend, InteractionHand.MAIN_HAND);
                    check(helper, count(friend, hair) == 1, () -> "a unicorn gave hair twice in a row");

                    // Kill it: no body yields hair, and the killer is marked.
                    float corruptionBefore = DarkCorruptionService.get(stranger);
                    unicorn.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(stranger), 1000f);
                    check(helper, !unicorn.isAlive(), () -> "the unicorn survived the test blow");
                    check(helper, itemsNear(helper, UNICORN_Y, hair).isEmpty(),
                            () -> "a slain unicorn dropped its hair");
                    check(helper, PlayerAbilityHelper.hasAbilityFlag(stranger, "unicorn_slayer"),
                            () -> "the unicorn's killer was not marked");
                    check(helper, DarkCorruptionService.get(stranger) > corruptionBefore,
                            () -> "killing a unicorn left no stain");
                    check(helper, stranger.hasEffect(MobEffects.WEAKNESS), () -> "the slayer carries no curse");

                    second[0] = still(helper, "unicorn", UNICORN_Y, 2, 6);
                    BestiaryDataHelper.setTier(stranger, unicornPage, DiscoveryTier.KNOWN);
                    stranger.setShiftKeyDown(true);
                    second[0].interact(stranger, InteractionHand.MAIN_HAND);
                    check(helper, count(stranger, hair) == 0,
                            () -> "a unicorn let a unicorn slayer near, however well they knew unicorns");
                })
                .thenExecute(() -> {
                    if (second[0] != null) {
                        second[0].discard();
                    }
                    retire(helper, stranger);
                    retire(helper, friend);
                })
                .thenSucceed();
    }

    // ── the demiguise ─────────────────────────────────────────────────────────────────────────────

    private static void demiguiseSeesTheObviousComing(GameTestHelper helper) {
        stage(helper, DEMIGUISE_Y);
        ServerPlayer seeker = watcher(helper, "wandb-creature-seeker", DEMIGUISE_Y, 2, 0);
        GenericBeastEntity demiguise = still(helper, "demiguise", DEMIGUISE_Y, 2, 4);
        Item hair = item("demiguise_hair");
        Vec3[] placed = new Vec3[1];

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(DEMIGUISE_Y), max(DEMIGUISE_Y)))
                .thenExecute(() -> lookAt(seeker, demiguise))
                .thenIdle(6)
                .thenExecute(() -> {
                    check(helper, demiguise.isInvisible() || demiguise.hasEffect(MobEffects.INVISIBILITY),
                            () -> "a demiguise stayed visible while it was stared at");
                    lookAway(seeker, demiguise);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    check(helper, !demiguise.hasEffect(MobEffects.INVISIBILITY),
                            () -> "a demiguise stayed invisible with nobody looking at it");

                    // Walking sideways past it is not foreseen.
                    placed[0] = demiguise.position();
                    seeker.setKnownMovement(new Vec3(0.25, 0, 0));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(helper, demiguise.position().distanceToSqr(placed[0]) < 0.01,
                            () -> "a demiguise moved for someone walking past it sideways");
                    // Walking straight at it is.
                    seeker.setKnownMovement(new Vec3(0, 0, 0.25));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    seeker.setKnownMovement(Vec3.ZERO);
                    check(helper, demiguise.position().distanceToSqr(placed[0]) > 4.0,
                            () -> "a demiguise did not foresee someone walking straight at it: still at "
                                    + demiguise.position());

                    // Reached unforeseen: it gives up a tuft, and the page is complete.
                    demiguise.interact(seeker, InteractionHand.MAIN_HAND);
                    check(helper, count(seeker, hair) == 1, () -> "a demiguise caught unforeseen gave nothing");
                    check(helper, tier(seeker, page("demiguise")) == DiscoveryTier.KNOWN,
                            () -> "catching a demiguise did not complete its page");

                    // It sheds where it rests. The sidestep can have carried it to the edge of the forced chunks, where
                    // it would stop ticking and never shed for a reason that is not the shedding: put it back first.
                    demiguise.snapTo(placed[0].x, placed[0].y, placed[0].z, 0.0f, 0.0f);
                    demiguise.setCooldown("shed", 1);
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    List<ItemEntity> signs = itemsNear(helper, DEMIGUISE_Y, hair);
                    check(helper, signs.size() == 1 && CreatureSign.speciesOf(signs.getFirst()) == demiguise.getType(),
                            () -> "a calm demiguise shed no marked tuft: " + signs);

                    demiguise.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(seeker), 1000f);
                    check(helper, itemsNear(helper, DEMIGUISE_Y, hair).size() == signs.size(),
                            () -> "a slain demiguise dropped its hair");
                    signs.forEach(Entity::discard);
                })
                .thenExecute(() -> retire(helper, seeker))
                .thenSucceed();
    }

    // ── the mooncalf ──────────────────────────────────────────────────────────────────────────────

    private static void mooncalfDancesUnderTheFullMoon(GameTestHelper helper) {
        stage(helper, MOONCALF_Y);
        ServerPlayer watcher = watcher(helper, "wandb-creature-moonwatcher", MOONCALF_Y, 2, 0);
        MooncalfEntity wild = helper.spawn(ModEntities.MOONCALF.get(), new BlockPos(2, MOONCALF_Y, 4));
        MooncalfEntity kept = helper.spawn(ModEntities.MOONCALF.get(), new BlockPos(5, MOONCALF_Y, 6));
        wild.setNoAi(true);
        kept.setNoAi(true);
        Item dung = item("mooncalf_dung");

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(MOONCALF_Y), max(MOONCALF_Y)))
                .thenExecute(() -> {
                    kept.bondState().setOwner(watcher.getUUID());
                    wild.refreshMoon(helper.getLevel(), false);
                    kept.refreshMoon(helper.getLevel(), false);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    check(helper, wild.isBurrowed() && wild.isInvisible(),
                            () -> "a wild mooncalf was out in the open with no full moon");
                    check(helper, !kept.isBurrowed() && !kept.isInvisible(),
                            () -> "a kept mooncalf hid from its keeper");
                    BestiaryDiscoveryHandler.scan(watcher);
                    check(helper, tier(watcher, page("mooncalf")) == DiscoveryTier.ENCOUNTERED,
                            () -> "only the kept mooncalf should have been seen: " + tier(watcher, page("mooncalf")));

                    wild.refreshMoon(helper.getLevel(), true);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    check(helper, !wild.isInvisible(), () -> "a mooncalf stayed burrowed under the full moon");
                    wild.refreshMoon(helper.getLevel(), true);
                    check(helper, wild.dancedTonight(), () -> "a mooncalf standing in its herd did not dance");
                    check(helper, tier(watcher, page("mooncalf")) == DiscoveryTier.KNOWN,
                            () -> "watching the dance did not complete the page: " + tier(watcher, page("mooncalf")));

                    wild.refreshMoon(helper.getLevel(), false);
                    List<ItemEntity> left = itemsNear(helper, MOONCALF_Y, dung);
                    check(helper, left.size() == 1, () -> "the dance left no dung at dawn: " + left);
                    check(helper, !wild.dancedTonight(), () -> "the night's dance was not settled");
                    left.forEach(Entity::discard);

                    MooncalfEntity restored = roundTrip(helper, wild);
                    check(helper, restored != null && wild.burrow() != null && wild.burrow().equals(restored.burrow()),
                            () -> "the burrow did not survive a save: " + (restored == null ? null : restored.burrow()));
                    if (restored != null) {
                        restored.discard();
                    }
                    wild.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(watcher), 1000f);
                    check(helper, itemsNear(helper, MOONCALF_Y, dung).isEmpty(), () -> "a slain mooncalf dropped dung");
                })
                .thenExecute(() -> {
                    kept.discard();
                    retire(helper, watcher);
                })
                .thenSucceed();
    }

    // ── the phoenix ───────────────────────────────────────────────────────────────────────────────

    private static void phoenixRisesFromItsAshes(GameTestHelper helper) {
        stage(helper, PHOENIX_Y);
        ServerPlayer friend = WizardTestSupport.placeMockPlayer(helper, "wandb-creature-phoenix-friend", GameType.SURVIVAL);
        place(helper, friend, PHOENIX_Y, 2, 0);
        PhoenixEntity phoenix = helper.spawn(ModEntities.PHOENIX.get(), new BlockPos(2, PHOENIX_Y, 4));
        phoenix.setNoAi(true);
        Item feather = item("phoenix_feather");

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(PHOENIX_Y), max(PHOENIX_Y)))
                .thenExecute(() -> lookAt(friend, phoenix))
                .thenExecute(() -> {
                    phoenix.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(friend), 1000f);
                    check(helper, phoenix.isAlive() && !phoenix.isRemoved(),
                            () -> "a phoenix died instead of being reborn");
                    check(helper, phoenix.isReborn() && phoenix.getScale() < 1.0f,
                            () -> "a reborn phoenix is not a chick: scale " + phoenix.getScale());
                    check(helper, itemsNear(helper, PHOENIX_Y, feather).isEmpty(),
                            () -> "a phoenix's rebirth dropped a feather as if it had been killed");
                    check(helper, tier(friend, page("phoenix")) == DiscoveryTier.KNOWN,
                            () -> "seeing a phoenix reborn did not complete its page: " + tier(friend, page("phoenix")));
                    check(helper, phoenix.bondState().level() == 0, () -> "hurting a phoenix did not cost its trust");

                    phoenix.defendedBy(friend);
                    check(helper, friend.getUUID().equals(phoenix.bondState().ownerUUID()) && phoenix.bondLevel() > 0,
                            () -> "defending a phoenix did not earn its loyalty");

                    phoenix.bondState().setLevel(PhoenixEntity.TEARS_BOND, 100);
                    friend.setHealth(friend.getMaxHealth() * 0.2f);
                    float hurt = friend.getHealth();
                    check(helper, phoenix.weepIfNeeded(helper.getLevel()) && friend.getHealth() > hurt,
                            () -> "a loyal phoenix did not weep for its badly hurt friend");
                    check(helper, !phoenix.weepIfNeeded(helper.getLevel()), () -> "phoenix tears have no cooldown");

                    PhoenixEntity restored = roundTrip(helper, phoenix);
                    check(helper, restored != null && restored.isReborn()
                                    && friend.getUUID().equals(restored.bondState().ownerUUID()),
                            () -> "rebirth or loyalty did not survive a save");
                    if (restored != null) {
                        restored.discard();
                    }

                    phoenix.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
                    check(helper, !phoenix.isAlive(), () -> "a phoenix survived something nothing is reborn from");
                })
                .thenExecute(() -> retire(helper, friend))
                .thenSucceed();
    }

    // ── the bowtruckle ────────────────────────────────────────────────────────────────────────────

    private static void bowtruckleDefendsItsTree(GameTestHelper helper) {
        stage(helper, BOWTRUCKLE_Y);
        for (int dy = 0; dy < 4; dy++) {
            helper.setBlock(new BlockPos(3, BOWTRUCKLE_Y + dy, 4), Blocks.OAK_LOG);
        }
        ServerPlayer woodcutter = watcher(helper, "wandb-creature-woodcutter", BOWTRUCKLE_Y, 1, 0);
        ServerPlayer wandmaker = watcher(helper, "wandb-creature-wandmaker", BOWTRUCKLE_Y, 5, 0);
        BowtruckleEntity guardian = helper.spawn(ModEntities.BOWTRUCKLE.get(), new BlockPos(2, BOWTRUCKLE_Y, 4));
        guardian.setNoAi(true);
        BlockPos log = helper.absolutePos(new BlockPos(3, BOWTRUCKLE_Y + 3, 4));
        BlockPos lowerLog = helper.absolutePos(new BlockPos(3, BOWTRUCKLE_Y + 2, 4));

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BOWTRUCKLE_Y), max(BOWTRUCKLE_Y)))
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper, guardian.homeTree() != null,
                            () -> "a bowtruckle beside a tree found no home in it");
                    lookAt(woodcutter, guardian);
                    guardian.bondState().setOwner(wandmaker.getUUID());

                    wandmaker.gameMode.destroyBlock(lowerLog);
                    check(helper, !guardian.isDefending(), () -> "a bowtruckle turned on its own wandmaker");

                    woodcutter.gameMode.destroyBlock(log);
                    check(helper, guardian.isDefending() && woodcutter.getUUID().equals(guardian.angryAt()),
                            () -> "a bowtruckle let someone cut its tree");
                    check(helper, tier(woodcutter, page("bowtruckle")) == DiscoveryTier.KNOWN,
                            () -> "seeing a bowtruckle defend its tree did not complete the page");

                    BowtruckleEntity restored = roundTrip(helper, guardian);
                    check(helper, restored != null && guardian.homeTree().equals(restored.homeTree())
                                    && restored.isDefending(),
                            () -> "the home tree or the grudge did not survive a save");
                    if (restored != null) {
                        restored.discard();
                    }
                })
                .thenExecute(() -> {
                    guardian.discard();
                    retire(helper, woodcutter);
                    retire(helper, wandmaker);
                })
                .thenSucceed();
    }

    // ── the niffler ───────────────────────────────────────────────────────────────────────────────

    private static void nifflerWantsGoldMost(GameTestHelper helper) {
        stage(helper, NIFFLER_Y);
        NifflerEntity niffler = helper.spawn(ModEntities.NIFFLER.get(), new BlockPos(2, NIFFLER_Y, 2));
        niffler.setNoAi(true);
        ItemEntity copper = drop(helper, Items.COPPER_INGOT, NIFFLER_Y, 3, 2);
        ItemEntity gold = drop(helper, Items.GOLD_INGOT, NIFFLER_Y, 5, 6);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(NIFFLER_Y), max(NIFFLER_Y)))
                .thenExecute(() -> {
                    ItemEntity wanted = new NifflerSeekShinyItemGoal(niffler).findPreferred();
                    check(helper, wanted == gold, () -> "a niffler chose " + (wanted == null ? "nothing"
                            : wanted.getItem()) + " over gold");

                    AABB around = niffler.getBoundingBox().inflate(8);
                    int babies = helper.getLevel().getEntitiesOfClass(BabyNifflerEntity.class, around).size();
                    NifflerEventHandler.onNifflerJoinLevel(new EntityJoinLevelEvent(niffler, helper.getLevel(), true));
                    check(helper, helper.getLevel().getEntitiesOfClass(BabyNifflerEntity.class, around).size() == babies,
                            () -> "a niffler loaded from disk brought a new litter with it");
                })
                .thenExecute(() -> {
                    copper.discard();
                    gold.discard();
                    niffler.discard();
                    helper.getLevel().getEntitiesOfClass(BabyNifflerEntity.class,
                            niffler.getBoundingBox().inflate(8)).forEach(Entity::discard);
                })
                .thenSucceed();
    }

    // ── the werewolf ──────────────────────────────────────────────────────────────────────────────

    private static void werewolfBiteCarriesTheCurse(GameTestHelper helper) {
        stage(helper, WEREWOLF_Y);
        ServerPlayer wizard = watcher(helper, "wandb-creature-bitten", WEREWOLF_Y, 1, 0);
        ServerPlayer goblin = watcher(helper, "wandb-creature-goblin", WEREWOLF_Y, 5, 0);
        GenericBeastEntity werewolf = still(helper, "werewolf", WEREWOLF_Y, 2, 5);
        ItemStack keepsake = new ItemStack(Items.DIAMOND, 3);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(WEREWOLF_Y), max(WEREWOLF_Y)))
                .thenExecute(() -> {
                    wizard.getData(ModAttachments.HERITAGE_DATA.get()).setSelectedHeritage(Heritage.WIZARDKIND);
                    wizard.getData(ModAttachments.HERITAGE_DATA.get()).setSelectedHeritageVariant(HeritageVariant.HALF_BLOOD);
                    goblin.getData(ModAttachments.HERITAGE_DATA.get()).setSelectedHeritage(Heritage.GOBLIN);
                    wizard.getInventory().add(keepsake.copy());
                    int power = PlayerStatsAPI.getData(wizard).power();

                    check(helper, !LycanthropyInfection.bitten(wizard, 3f, false),
                            () -> "a bite with no full moon passed on the curse");
                    check(helper, !LycanthropyInfection.bitten(goblin, 3f, true),
                            () -> "a goblin caught lycanthropy, which only humans catch");
                    check(helper, LycanthropyInfection.bitten(wizard, 3f, true),
                            () -> "a transformed werewolf's bite under a full moon did not curse a human");

                    var data = wizard.getData(ModAttachments.HERITAGE_DATA.get());
                    check(helper, data.getCondition() == at.koopro.wizardsandbeasts.heritage.ConditionOrigin.BITTEN
                                    && data.getSelectedHeritage() == Heritage.WIZARDKIND
                                    && data.getSelectedHeritageVariant() == HeritageVariant.HALF_BLOOD,
                            () -> "the bitten wizard should be the same half-blood witch or wizard, now carrying lycanthropy: "
                                    + data.getSelectedHeritage() + "/" + data.getSelectedHeritageVariant() + " + "
                                    + data.getCondition());
                    check(helper, PlayerStatsAPI.getData(wizard).power() == power,
                            () -> "the curse re-rolled who the wizard is: power " + power + " became "
                                    + PlayerStatsAPI.getData(wizard).power());
                    check(helper, wizard.getInventory().countItem(Items.DIAMOND) == keepsake.getCount(),
                            () -> "the curse took the wizard's belongings");
                    check(helper, WerewolfState.inOnset(data, WerewolfRules.nightOf(helper.getLevel().getDayTime())),
                            () -> "a werewolf bitten tonight would change tonight; the first change waits a month");
                    check(helper, !LycanthropyInfection.bitten(wizard, 3f, true),
                            () -> "a werewolf caught the curse a second time");

                    // A werewolf creature is only out under a full moon.
                    boolean moonUp = WerewolfRules.fullMoonNight(helper.getLevel());
                    if (!moonUp) {
                        MoonBound.leave(werewolf, helper.getLevel());
                    }
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    boolean moonUp = WerewolfRules.fullMoonNight(helper.getLevel());
                    check(helper, moonUp != werewolf.isRemoved(),
                            () -> "a werewolf creature " + (moonUp ? "left under a full moon" : "stayed out with no full moon"));
                    check(helper, itemsNear(helper, WEREWOLF_Y, null).isEmpty(),
                            () -> "a werewolf that left at dawn dropped something");
                    werewolf.discard();
                })
                .thenExecute(() -> {
                    retire(helper, wizard);
                    retire(helper, goblin);
                })
                .thenSucceed();
    }

    // ── shared ────────────────────────────────────────────────────────────────────────────────────

    private static BlockPos min(int y) {
        return new BlockPos(-1, y - 1, -1);
    }

    private static BlockPos max(int y) {
        return new BlockPos(7, y + 5, 8);
    }

    private static void stage(GameTestHelper helper, int y) {
        WizardTestSupport.forceChunks(helper, min(y), max(y));
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE);
                for (int dy = 0; dy <= 4; dy++) {
                    helper.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR);
                }
            }
        }
    }

    /** A creative test player standing at the stage's near edge, looking along +Z. */
    private static ServerPlayer watcher(GameTestHelper helper, String name, int y, int x, int z) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.CREATIVE);
        place(helper, player, y, x, z);
        return player;
    }

    private static void place(GameTestHelper helper, ServerPlayer player, int y, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        player.setNoGravity(true);
    }

    private static GenericBeastEntity still(GameTestHelper helper, String id, int y, int x, int z) {
        @SuppressWarnings("unchecked")
        EntityType<GenericBeastEntity> type = (EntityType<GenericBeastEntity>) ModCreatures.ENTITIES.get(id).get();
        GenericBeastEntity creature = helper.spawn(type, new BlockPos(x, y, z));
        creature.setNoAi(true);
        return creature;
    }

    private static ItemEntity drop(GameTestHelper helper, Item item, int y, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
        ItemEntity entity = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                new ItemStack(item));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void lookAt(ServerPlayer player, Entity target) {
        Vec3 to = target.getEyePosition().subtract(player.getEyePosition());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        player.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, horizontal)));
        player.setYHeadRot(player.getYRot());
    }

    private static void lookAway(ServerPlayer player, Entity target) {
        lookAt(player, target);
        player.setYRot(player.getYRot() + 180.0f);
        player.setYHeadRot(player.getYRot());
    }

    private static Identifier page(String id) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id);
    }

    private static DiscoveryTier tier(ServerPlayer player, Identifier page) {
        return BestiaryDataHelper.getTier(player, page);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** Item entities on this scenario's stage, of {@code item} or of anything when it is {@code null}. */
    private static List<ItemEntity> itemsNear(GameTestHelper helper, int y, Item item) {
        Vec3 lo = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(-2, y - 2, -2)));
        Vec3 hi = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(9, y + 6, 10)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(lo, hi),
                entity -> entity.isAlive() && (item == null || entity.getItem().is(item)));
    }

    /** Saves a creature and loads a copy of it, the way a chunk does. The copy is not added to the world. */
    @SuppressWarnings("unchecked")
    private static <T extends Entity> T roundTrip(GameTestHelper helper, T entity) {
        ServerLevel level = helper.getLevel();
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(entity.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            entity.saveWithoutId(output);
            var tag = output.buildResult();
            Supplier<T> load = () -> (T) EntityType.create(entity.getType(),
                    TagValueInput.create(reporter, level.registryAccess(), tag), level, EntitySpawnReason.LOAD)
                    .orElse(null);
            return load.get();
        }
    }
}
