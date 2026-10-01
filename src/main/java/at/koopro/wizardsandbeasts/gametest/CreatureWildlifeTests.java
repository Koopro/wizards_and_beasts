package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.creature.ai.KelpieLureGoal;
import at.koopro.wizardsandbeasts.entity.creature.KelpieEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import at.koopro.wizardsandbeasts.spell.lib.ColloportusLockStore;
import at.koopro.wizardsandbeasts.entity.beast.BowtruckleLockpickGoal;
import at.koopro.wizardsandbeasts.entity.beast.BowtruckleHideGoal;
import net.minecraft.world.level.gamerules.GameRules;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerHoardGoal;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerSeekShinyBlockGoal;
import net.minecraft.world.level.block.state.BlockState;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.creature.ability.WebSnare;
import at.koopro.wizardsandbeasts.entity.creature.ai.WebSnareGoal;
import at.koopro.wizardsandbeasts.entity.creature.AcromantulaWebs;
import at.koopro.wizardsandbeasts.entity.creature.AcromantulaEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import at.koopro.wizardsandbeasts.spell.petrify.PetrifyServerLogic;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.item.darkartefact.HorcruxDestruction;
import at.koopro.wizardsandbeasts.entity.creature.ai.DeathGazeGoal;
import at.koopro.wizardsandbeasts.entity.creature.BasiliskEntity;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixTears;
import at.koopro.wizardsandbeasts.effect.BasiliskGazeLockEffect;
import at.koopro.wizardsandbeasts.chamber.ChamberBasilisk;
import at.koopro.wizardsandbeasts.basilisk.BasiliskGaze;
import at.koopro.wizardsandbeasts.basilisk.BasiliskDamageTypes;
import java.util.ArrayList;
import net.minecraft.world.entity.animal.cow.Cow;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorKissGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import at.koopro.wizardsandbeasts.entity.creature.HippogriffEntity;
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
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixFlameTravel;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixRebirth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ExperienceOrb;
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
    // Low on purpose: it places blocks (a wall, water), and a stage above relative y ~370 is above the world's build
    // height — setBlock does nothing there, which the scenarios at 400+ get away with only because they place none.
    private static final int BASILISK_Y = 124;
    private static final int NIFFLER_Y = 137;
    private static final int NIFFLER_DIG_Y = 212;
    private static final int WEREWOLF_Y = 162;
    private static final int UNICORN_Y = 187;
    private static final int DEMIGUISE_Y = 225;
    private static final int ACROMANTULA_Y = 237;
    private static final int MOONCALF_Y = 275;
    private static final int KELPIE_Y = 262;
    private static final int PHOENIX_Y = 325;
    private static final int BOWTRUCKLE_Y = 362;
    private static final int BOWTRUCKLE_HIDE_Y = 175;
    private static final int HIPPOGRIFF_Y = 400;
    private static final int THESTRAL_Y = 437;
    private static final int DEMENTOR_Y = 475;

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
        tests.add("creature_acromantula_colony_silk_and_venom",
                "acromantula: joins the colony it finds, never turns on its kin; one venom per bite; silk only where it "
                        + "can hang, capped at the nest, crumbles with no spider near; a snare leaves one web; drops venom",
                CreatureWildlifeTests::acromantulaColonySilkAndVenom);
        tests.add("creature_basilisk_eyes_fangs_and_venom",
                "basilisk: direct eyes kill once, water petrifies, walls/averted eyes/blindfold/blindness stop it, a "
                        + "broken stare resolves nothing; a bite's venom deepens, resists milk, yields to phoenix tears; "
                        + "a fang ends a Horcrux; no Chamber, no waking",
                CreatureWildlifeTests::basiliskEyesFangsAndVenom);
        tests.add("creature_dementor_chills_by_distance_and_flees_the_light",
                "dementor: chill by distance band, a swarm deepens it once, one Kiss per soul that nothing cures, "
                        + "a Patronus wards by strength and drives it off, blows do nothing, dissipation unravels it",
                CreatureWildlifeTests::dementorChillsByDistanceAndFleesTheLight);
        tests.add("creature_thestral_is_real_to_everyone",
                "thestral: a person's death seen grants sight, a cow's does not; unseen, it is still fed, ridden and struck",
                CreatureWildlifeTests::thestralIsRealToEveryone);
        tests.add("creature_hippogriff_answers_a_bow",
                "hippogriff: bows back to a bow, warns then attacks a stranger who crowds it, only the respected may feed and ride it",
                CreatureWildlifeTests::hippogriffAnswersABow);
        tests.add("creature_phoenix_rises_from_its_ashes",
                "phoenix: burns to ashes and rises once per death with no drops, weeps and sings for its person, travels by flame",
                CreatureWildlifeTests::phoenixRisesFromItsAshes);
        tests.add("creature_bowtruckle_defends_its_tree",
                "bowtruckle: finds a home tree, turns on whoever cuts it, spares its bonded wandmaker",
                CreatureWildlifeTests::bowtruckleDefendsItsTree);
        tests.add("creature_kelpie_lures_grips_and_lets_go",
                "kelpie: a disguised horse lets you on and off, then grips — no dismounting — and drowns only a head "
                        + "under water; a hard blow or the grip's end throws you off, and it will not lure again for a "
                        + "while; only a subdued kelpie takes a bridle, and a bridled one is steered, not a trap",
                CreatureWildlifeTests::kelpieLuresGripsAndLetsGo);
        tests.add("creature_bowtruckle_hides_and_picks_locks",
                "bowtruckle: goes faint (never invisible) when still against bark, startles at strangers and hides at its "
                        + "tree; picks an iron lock for its trusted person only, never a Colloportus seal; whoever fed it "
                        + "may cut its tree, anyone else is set on",
                CreatureWildlifeTests::bowtruckleHidesAndPicksLocks);
        tests.add("creature_niffler_wants_gold_most",
                "niffler: goes for gold before nearer copper; a saved niffler brings no new litter on load",
                CreatureWildlifeTests::nifflerWantsGoldMost);
        tests.add("creature_niffler_digs_pockets_and_hoards",
                "niffler: takes only what fits (the rest stays on the ground), leaves fresh drops and junk alone; mines "
                        + "ore for real and burrows through loose earth to it, never a gold block or through stone, not "
                        + "with mobGriefing off; its pouch survives a save, a growing-up and its death",
                CreatureWildlifeTests::nifflerDigsPocketsAndHoards);
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
                    // The cursed life belongs to whoever drinks the blood (Philosopher's Stone ch. 15); the killer is
                    // marked and stained, not cursed (CANON_AUDIT C-6).
                    check(helper, !stranger.hasEffect(MobEffects.WEAKNESS), () -> "the slayer was cursed for the killing");

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
        int[] ashesLeft = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(PHOENIX_Y), max(PHOENIX_Y)))
                .thenExecute(() -> lookAt(friend, phoenix))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    phoenix.hurtServer(level, level.damageSources().playerAttack(friend), 1000f);
                    check(helper, phoenix.isAlive() && !phoenix.isRemoved() && phoenix.getHealth() > 0,
                            () -> "a phoenix died instead of bursting into flame");
                    check(helper, phoenix.rebirth().phase() == PhoenixRebirth.Phase.ASHES
                                    && phoenix.syncedPhase() == PhoenixRebirth.Phase.ASHES,
                            () -> "a burning phoenix is not ashes: " + phoenix.rebirth().phase());
                    check(helper, itemsNear(helper, PHOENIX_Y, null).isEmpty(),
                            () -> "a phoenix burning dropped something as if it had been killed");
                    check(helper, level.getEntitiesOfClass(ExperienceOrb.class, phoenix.getBoundingBox().inflate(6)).isEmpty(),
                            () -> "a phoenix burning gave experience");
                    check(helper, level.getEntitiesOfClass(PhoenixEntity.class, phoenix.getBoundingBox().inflate(8)).size() == 1,
                            () -> "a burning made a second phoenix");
                    check(helper, tier(friend, page("phoenix")) == DiscoveryTier.KNOWN,
                            () -> "seeing a phoenix burn did not complete its page: " + tier(friend, page("phoenix")));
                    check(helper, phoenix.bondState().level() == 0, () -> "hurting a phoenix did not cost its trust");

                })
                .thenIdle(5)
                .thenExecute(() -> {
                    // Ashes cannot be hurt, and a second death blow does not restart the burning. Struck a few
                    // ticks later with the hurt cooldown cleared, so vanilla's i-frames cannot be what stops it.
                    ServerLevel level = helper.getLevel();
                    phoenix.invulnerableTime = 0;
                    ashesLeft[0] = phoenix.rebirth().ticksLeft();
                    boolean hurtAgain = phoenix.hurtServer(level, level.damageSources().playerAttack(friend), 1000f);
                    check(helper, !hurtAgain && phoenix.rebirth().phase() == PhoenixRebirth.Phase.ASHES
                                    && phoenix.rebirth().ticksLeft() == ashesLeft[0]
                                    && ashesLeft[0] < PhoenixRebirth.ASHES_TICKS,
                            () -> "ashes were hurt, or a second blow restarted the burning");

                    PhoenixEntity saved = roundTrip(helper, phoenix);
                    check(helper, saved != null && saved.rebirth().phase() == PhoenixRebirth.Phase.ASHES
                                    && saved.rebirth().ticksLeft() == phoenix.rebirth().ticksLeft()
                                    && saved.syncedPhase() == PhoenixRebirth.Phase.ASHES,
                            () -> "a burning did not survive a save");
                })
                .thenIdle(PhoenixRebirth.ASHES_TICKS + PhoenixRebirth.RISING_TICKS)
                .thenExecute(() -> {
                    check(helper, phoenix.rebirth().phase() == PhoenixRebirth.Phase.ALIVE,
                            () -> "the phoenix did not rise: " + phoenix.rebirth().phase());
                    check(helper, phoenix.isReborn() && phoenix.getScale() < 1.0f,
                            () -> "a reborn phoenix is not a chick: scale " + phoenix.getScale());
                    check(helper, Math.abs(phoenix.getHealth()
                                    - phoenix.getMaxHealth() * WildlifeRules.REBORN_HEALTH_FRACTION) < 0.01f,
                            () -> "a reborn chick is not weak: health " + phoenix.getHealth());
                    check(helper, itemsNear(helper, PHOENIX_Y, feather).isEmpty(),
                            () -> "the rising dropped a feather");
                    check(helper, helper.getLevel().getEntitiesOfClass(PhoenixEntity.class,
                                    phoenix.getBoundingBox().inflate(8)).size() == 1,
                            () -> "the rising made a second phoenix");
                })
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    phoenix.defendedBy(friend);
                    check(helper, friend.getUUID().equals(phoenix.bondState().ownerUUID()) && phoenix.bondLevel() > 0,
                            () -> "defending a phoenix did not earn its loyalty");

                    // Tears: a loyal phoenix weeps over its person, healing them and drawing out poison.
                    phoenix.bondState().setLevel(PhoenixEntity.TEARS_BOND, 100);
                    friend.setHealth(friend.getMaxHealth() * 0.2f);
                    friend.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
                    float hurt = friend.getHealth();
                    check(helper, phoenix.weepIfNeeded(level) && friend.getHealth() > hurt
                                    && !friend.hasEffect(MobEffects.POISON),
                            () -> "a loyal phoenix did not weep for its hurt, poisoned friend");
                    check(helper, !phoenix.weepIfNeeded(level), () -> "phoenix tears have no cooldown");

                    // Song: courage for its person, once, then a long rest.
                    check(helper, phoenix.sing(level) && phoenix.singing()
                                    && friend.hasEffect(MobEffects.RESISTANCE),
                            () -> "a phoenix song gave its person no courage");
                    check(helper, !phoenix.sing(level), () -> "a phoenix sang again straight away");

                    // Flame travel: a safe landing across the stage, then a cooldown; nothing outside the world.
                    Vec3 before = phoenix.position();
                    var landing = PhoenixFlameTravel.landingNear(level, phoenix,
                            Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, PHOENIX_Y, 7))), 2);
                    check(helper, landing.isPresent(), () -> "no landing found on an open stage");
                    landing.ifPresent(target -> phoenix.flameTravel(level, target));
                    check(helper, phoenix.position().distanceTo(before) > 2.0 && !phoenix.canFlameTravel(),
                            () -> "flame travel did not move the phoenix, or has no cooldown");
                    check(helper, PhoenixFlameTravel.landingNear(level, phoenix,
                                    new Vec3(before.x, level.getMaxY() + 20, before.z), 2).isEmpty(),
                            () -> "flame travel accepted a destination above the world");

                    // It burns every time it dies, not once.
                    phoenix.invulnerableTime = 0;
                    phoenix.hurtServer(level, level.damageSources().playerAttack(friend), 1000f);
                    check(helper, phoenix.isAlive() && phoenix.rebirth().phase() == PhoenixRebirth.Phase.ASHES,
                            () -> "a phoenix did not burn a second time");

                    phoenix.invulnerableTime = 0;
                    phoenix.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
                    check(helper, !phoenix.isAlive(), () -> "a phoenix survived something nothing is reborn from");
                })
                .thenExecute(() -> retire(helper, friend))
                .thenSucceed();
    }

    // ── the acromantula ───────────────────────────────────────────────────────────────────────────

    private static void acromantulaColonySilkAndVenom(GameTestHelper helper) {
        stage(helper, ACROMANTULA_Y);
        ServerPlayer prey = WizardTestSupport.placeMockPlayer(helper, "wandb-acromantula-prey", GameType.SURVIVAL);
        place(helper, prey, ACROMANTULA_Y, 6, 7);
        List<AcromantulaEntity> spiders = new ArrayList<>();
        EntityType<?> type = ModCreatures.ENTITIES.get("acromantula").get();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(ACROMANTULA_Y), max(ACROMANTULA_Y)))
                .thenExecute(() -> {
                    AcromantulaEntity founder = (AcromantulaEntity) helper.spawn(type, new BlockPos(1, ACROMANTULA_Y, 1));
                    founder.setNoAi(true);
                    spiders.add(founder);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    AcromantulaEntity founder = spiders.get(0);
                    check(helper, founder.hasHome() && founder.getHomeRadius() == AcromantulaEntity.TERRITORY,
                            () -> "a lone spider founded no colony");
                    AcromantulaEntity kin = (AcromantulaEntity) helper.spawn(type, new BlockPos(4, ACROMANTULA_Y, 2));
                    kin.setNoAi(true);
                    spiders.add(kin);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    AcromantulaEntity founder = spiders.get(0);
                    AcromantulaEntity kin = spiders.get(1);
                    check(helper, kin.sameColony(founder) && kin.getHomePosition().equals(founder.getHomePosition()),
                            () -> "a spider beside a colony founded its own instead of joining");
                    check(helper, !kin.canAttack(founder), () -> "an acromantula could turn on its own kind");

                    // Venom: a bite leaves Poison II, once — no second copy from the old trait.
                    prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
                    prey.setHealth(200.0f);
                    check(helper, founder.doHurtTarget(level, prey), () -> "the bite missed");
                    MobEffectInstance venom = prey.getEffect(MobEffects.POISON);
                    check(helper, venom != null && venom.getAmplifier() == 1, () -> "the bite left no Poison II: " + venom);

                    // Silk: only into air it can hang from, never twice in one place, never in mid-air.
                    BlockPos floorAir = helper.absolutePos(new BlockPos(2, ACROMANTULA_Y, 6));
                    BlockPos midAir = helper.absolutePos(new BlockPos(3, ACROMANTULA_Y + 3, 4));
                    check(helper, AcromantulaWebs.spinAt(level, floorAir) && level.getBlockState(floorAir).is(ModBlocks.ACROMANTULA_WEB.get()),
                            () -> "no silk on open floor");
                    check(helper, !AcromantulaWebs.canSpin(level, floorAir), () -> "silk spun over silk");
                    check(helper, !AcromantulaWebs.canSpin(level, midAir), () -> "silk hung from nothing");

                    // The nest is webbed, not filled.
                    for (int i = 0; i < 200; i++) {
                        AcromantulaWebs.webTheNest(level, founder.getHomePosition(), founder.getRandom());
                    }
                    int webs = AcromantulaWebs.count(level, founder.getHomePosition(), AcromantulaWebs.NEST_RADIUS);
                    check(helper, webs > 0 && webs <= AcromantulaWebs.NEST_CAP, () -> "the nest held " + webs + " webs");

                    // A snare slows its target and leaves exactly one web where it landed.
                    founder.setTarget(prey);
                    WebSnareGoal snare = new WebSnareGoal(founder, new WebSnare(10.0, 3, 100, 140));
                    BlockPos feet = prey.blockPosition();
                    snare.tick();
                    check(helper, prey.hasEffect(MobEffects.SLOWNESS), () -> "the snare did not slow its target");
                    check(helper, level.getBlockState(feet).is(ModBlocks.ACROMANTULA_WEB.get()), () -> "the snare left no silk");
                    snare.tick();
                    check(helper, level.getBlockState(feet.above()).isAir(), () -> "the snare spun again inside its cooldown");

                    // Kept while a spider lives near; crumbles once none does.
                    BlockState silk = level.getBlockState(floorAir);
                    silk.randomTick(level, floorAir, level.getRandom());
                    check(helper, level.getBlockState(floorAir).is(ModBlocks.ACROMANTULA_WEB.get()), () -> "kept silk crumbled");
                    AcromantulaEntity saved = roundTrip(helper, kin);
                    check(helper, saved != null && saved.hasHome() && saved.getHomePosition().equals(kin.getHomePosition()),
                            () -> "a spider's colony did not survive a save");
                })
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    AcromantulaEntity founder = spiders.get(0);
                    AcromantulaEntity kin = spiders.get(1);
                    kin.discard();
                    founder.hurtServer(level, level.damageSources().playerAttack(prey), 10_000.0f);
                    check(helper, !itemsNear(helper, ACROMANTULA_Y, item("acromantula_venom")).isEmpty(),
                            () -> "a dead acromantula dropped no venom");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    BlockPos floorAir = helper.absolutePos(new BlockPos(2, ACROMANTULA_Y, 6));
                    level.getBlockState(floorAir).randomTick(level, floorAir, level.getRandom());
                    check(helper, level.getBlockState(floorAir).isAir(), () -> "silk outlived its spiders");
                    itemsNear(helper, ACROMANTULA_Y, null).forEach(Entity::discard);
                    spiders.forEach(Entity::discard);
                    retire(helper, prey);
                })
                .thenSucceed();
    }

    // ── the basilisk ──────────────────────────────────────────────────────────────────────────────

    private static void basiliskEyesFangsAndVenom(GameTestHelper helper) {
        stage(helper, BASILISK_Y);
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        ServerPlayer direct = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-direct", GameType.SURVIVAL);
        ServerPlayer averted = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-averted", GameType.SURVIVAL);
        ServerPlayer walled = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-walled", GameType.SURVIVAL);
        ServerPlayer bitten = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-bitten", GameType.SURVIVAL);
        place(helper, direct, BASILISK_Y, 3, 8);
        place(helper, averted, BASILISK_Y, 6, 7);
        place(helper, walled, BASILISK_Y, 0, 7);
        place(helper, bitten, BASILISK_Y, 6, 3);
        List<Entity> spawned = new ArrayList<>();
        List<ServerPlayer> late = new ArrayList<>();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BASILISK_Y), max(BASILISK_Y)))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    // A wall of stone between the basilisk's head and one player.
                    for (int x = 0; x <= 2; x++) {
                        for (int z = 5; z <= 6; z++) {
                            for (int y = 1; y <= 2; y++) {
                                helper.setBlock(new BlockPos(x, BASILISK_Y + y, z), Blocks.STONE);
                            }
                        }
                    }
                    BasiliskEntity basilisk = (BasiliskEntity) helper.spawn(ModCreatures.ENTITIES.get("basilisk").get(),
                            new BlockPos(3, BASILISK_Y, 1));
                    basilisk.setNoAi(true);
                    // Toward the stage's far edge, whatever way the test structure was rotated.
                    Vec3 ahead = direct.position().subtract(basilisk.position());
                    face(basilisk, (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z)));
                    spawned.add(basilisk);
                    Vec3 eyes = basilisk.headPosition();
                    lookAtPoint(direct, eyes);
                    lookAtPoint(walled, eyes);
                    lookAtPoint(averted, eyes);
                    averted.setYRot(averted.getYRot() + 180.0f);
                    averted.setYHeadRot(averted.getYRot());
                    Vec3 facing = Vec3.directionFromRotation(0, basilisk.getYHeadRot());

                    check(helper, BasiliskGaze.evaluate(level, eyes, facing, direct) == BasiliskGaze.Outcome.DEATH,
                            () -> "meeting its eyes directly was not death");
                    check(helper, BasiliskGaze.evaluate(level, eyes, facing, averted) == BasiliskGaze.Outcome.NONE,
                            () -> "a player looking away was still caught");
                    check(helper, BasiliskGaze.evaluate(level, eyes, facing, walled) == BasiliskGaze.Outcome.NONE,
                            () -> "its gaze went through a stone wall");
                    // Turned away from the victim, it cannot catch them however they look at it.
                    check(helper, BasiliskGaze.evaluate(level, eyes, facing.scale(-1), direct) == BasiliskGaze.Outcome.NONE,
                            () -> "its gaze reached someone behind it");

                    // Held for the windup, the stare lands once: the direct viewer dies, no one else is touched.
                    DeathGazeGoal gaze = new DeathGazeGoal(basilisk);
                    tickGoal(gaze, DeathGazeGoal.INTERVAL * 2);
                    check(helper, direct.hasEffect(ModEffects.BASILISK_GAZE_LOCK), () -> "no heartbeat while the stare took hold");
                    check(helper, direct.isAlive(), () -> "the gaze killed before its windup");
                    tickGoal(gaze, BasiliskGazeLockEffect.WINDUP_TICKS);
                    check(helper, direct.isDeadOrDying(), () -> "meeting its eyes for the whole windup did not kill");
                    check(helper, direct.getLastDamageSource() != null
                                    && direct.getLastDamageSource().is(BasiliskDamageTypes.GAZE)
                                    && direct.getLastDamageSource().getEntity() == basilisk,
                            () -> "the death was not the basilisk's gaze: " + direct.getLastDamageSource());
                    check(helper, averted.isAlive() && walled.isAlive() && !PetrifyServerLogic.isPetrified(walled)
                                    && !averted.hasEffect(ModEffects.BASILISK_GAZE_LOCK),
                            () -> "the gaze touched someone who did not meet it");
                    retire(helper, direct);

                    // Seen through water, the same eyes petrify instead.
                    ServerPlayer mirrored = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-water", GameType.SURVIVAL);
                    late.add(mirrored);
                    place(helper, mirrored, BASILISK_Y, 5, 8);
                    lookAtPoint(mirrored, eyes);
                    helper.setBlock(new BlockPos(4, BASILISK_Y + 1, 6), Blocks.WATER);
                    check(helper, BasiliskGaze.evaluate(level, eyes, facing, mirrored) == BasiliskGaze.Outcome.PETRIFY,
                            () -> "seeing its eyes through water was " + BasiliskGaze.evaluate(level, eyes, facing, mirrored));
                    tickGoal(gaze, BasiliskGazeLockEffect.WINDUP_TICKS + DeathGazeGoal.INTERVAL);
                    check(helper, mirrored.isAlive() && PetrifyServerLogic.isPetrified(mirrored),
                            () -> "an indirect stare did not petrify (alive " + mirrored.isAlive() + ")");
                    helper.setBlock(new BlockPos(4, BASILISK_Y + 1, 6), Blocks.AIR);
                    // Stone sees nothing: with the water gone the statue faces the eyes directly and is not killed.
                    tickGoal(gaze, BasiliskGazeLockEffect.WINDUP_TICKS * 2);
                    check(helper, mirrored.isAlive(), () -> "a petrified player was killed by the eyes it faces");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    BasiliskEntity basilisk = (BasiliskEntity) spawned.get(0);
                    Vec3 eyes = basilisk.headPosition();
                    // A stare broken mid-windup resolves nothing.
                    ServerPlayer glancer = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-glancer", GameType.SURVIVAL);
                    late.add(glancer);
                    place(helper, glancer, BASILISK_Y, 3, 8);
                    lookAtPoint(glancer, eyes);
                    DeathGazeGoal gaze = new DeathGazeGoal(basilisk);
                    tickGoal(gaze, DeathGazeGoal.INTERVAL * 2);
                    check(helper, glancer.hasEffect(ModEffects.BASILISK_GAZE_LOCK), () -> "the glancer's stare never took hold");
                    glancer.setYRot(glancer.getYRot() + 180.0f);
                    glancer.setYHeadRot(glancer.getYRot());
                    tickGoal(gaze, DeathGazeGoal.INTERVAL);
                    check(helper, !glancer.hasEffect(ModEffects.BASILISK_GAZE_LOCK), () -> "looking away did not break the stare");
                    tickGoal(gaze, BasiliskGazeLockEffect.WINDUP_TICKS * 2);
                    check(helper, glancer.isAlive() && !PetrifyServerLogic.isPetrified(glancer),
                            () -> "a broken stare still resolved");

                    // Eyes shut: a blindfold, or blindness, and the stare cannot take hold at all.
                    lookAtPoint(glancer, eyes);
                    glancer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MiscItemRegistry.BLINDFOLD.get()));
                    tickGoal(gaze, BasiliskGazeLockEffect.WINDUP_TICKS * 2);
                    check(helper, glancer.isAlive() && !glancer.hasEffect(ModEffects.BASILISK_GAZE_LOCK),
                            () -> "a blindfolded player was caught");
                    glancer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    // A blinded basilisk has no gaze.
                    basilisk.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
                    check(helper, !gaze.canUse(), () -> "a blinded basilisk could still gaze");
                    basilisk.removeEffect(MobEffects.BLINDNESS);

                    // The bite: venom, deepened by a second, one instance, nothing but tears draws it out.
                    bitten.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
                    bitten.setHealth(200.0f);
                    check(helper, basilisk.doHurtTarget(level, bitten), () -> "the bite missed");
                    MobEffectInstance venom = bitten.getEffect(ModEffects.BASILISK_VENOM);
                    check(helper, venom != null && venom.getAmplifier() == 0, () -> "a bite left no venom: " + venom);
                    bitten.invulnerableTime = 0;
                    check(helper, basilisk.doHurtTarget(level, bitten), () -> "the second bite missed");
                    check(helper, bitten.getEffect(ModEffects.BASILISK_VENOM).getAmplifier() == 1,
                            () -> "a second bite did not deepen the venom");
                    bitten.removeAllEffects();
                    bitten.removeEffect(ModEffects.BASILISK_VENOM);
                    check(helper, bitten.hasEffect(ModEffects.BASILISK_VENOM), () -> "milk or a command drew out basilisk venom");
                    PhoenixEntity phoenix = helper.spawn(ModEntities.PHOENIX.get(), new BlockPos(6, BASILISK_Y, 5));
                    phoenix.setNoAi(true);
                    spawned.add(phoenix);
                    PhoenixTears.weepOver(level, phoenix, bitten);
                    check(helper, !bitten.hasEffect(ModEffects.BASILISK_VENOM), () -> "phoenix tears did not draw out the venom");

                    // A fang: a stab carries venom; used on a Horcrux in the other hand, it ends it.
                    ServerPlayer wielder = WizardTestSupport.placeMockPlayer(helper, "wandb-basilisk-wielder", GameType.SURVIVAL);
                    late.add(wielder);
                    place(helper, wielder, BASILISK_Y, 7, 3);
                    ItemStack fang = new ItemStack(CanonItemRegistry.BASILISK_FANG.get());
                    ItemStack diary = new ItemStack(DarkArtefactItemRegistry.RIDDLES_DIARY.get());
                    check(helper, HorcruxDestruction.isIntact(diary), () -> "a fresh diary had no soul fragment");
                    var cow = helper.spawn(EntityType.COW, new BlockPos(7, BASILISK_Y, 5));
                    cow.setNoAi(true);
                    spawned.add(cow);
                    fang.getItem().hurtEnemy(fang, cow, wielder);
                    check(helper, cow.hasEffect(ModEffects.BASILISK_VENOM), () -> "a fang's stab carried no venom");
                    wielder.setItemInHand(InteractionHand.MAIN_HAND, fang);
                    wielder.setItemInHand(InteractionHand.OFF_HAND, diary);
                    fang.getItem().use(level, wielder, InteractionHand.MAIN_HAND);
                    check(helper, !HorcruxDestruction.isIntact(wielder.getItemInHand(InteractionHand.OFF_HAND)),
                            () -> "the fang did not destroy the diary");
                    check(helper, wielder.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), () -> "the fang was not spent");

                    // No Chamber here: nothing may wake a basilisk on this stage, module on or off.
                    Runnable chamber = WizardTestSupport.leaseModule(Module.CHAMBER_OF_SECRETS);
                    check(helper, !ChamberBasilisk.mayWake(level, helper.absolutePos(new BlockPos(3, BASILISK_Y, 6)),
                            e -> e instanceof BasiliskEntity), () -> "a basilisk could wake outside a Chamber");
                    chamber.run();
                    // Its territory is vanilla's saved home.
                    check(helper, basilisk.hasHome() && basilisk.getHomeRadius() == BasiliskEntity.TERRITORY,
                            () -> "a basilisk had no territory");
                    BasiliskEntity saved = roundTrip(helper, basilisk);
                    check(helper, saved != null && saved.hasHome() && saved.getHomePosition().equals(basilisk.getHomePosition()),
                            () -> "a basilisk's lair did not survive a save");
                })
                .thenExecute(() -> {
                    spawned.forEach(Entity::discard);
                    retire(helper, averted);
                    retire(helper, walled);
                    retire(helper, bitten);
                    late.forEach(p -> retire(helper, p));
                    darkArts.run();
                })
                .thenSucceed();
    }

    private static void tickGoal(DeathGazeGoal goal, int ticks) {
        for (int i = 0; i < ticks; i++) {
            goal.tick();
        }
    }

    private static void face(BasiliskEntity basilisk, float yaw) {
        basilisk.setYRot(yaw);
        basilisk.setYHeadRot(yaw);
        basilisk.setYBodyRot(yaw);
    }

    private static void lookAtPoint(ServerPlayer player, Vec3 point) {
        Vec3 to = point.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        player.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, horizontal)));
        player.setYHeadRot(player.getYRot());
    }

    // ── the dementor ──────────────────────────────────────────────────────────────────────────────

    private static void dementorChillsByDistanceAndFleesTheLight(GameTestHelper helper) {
        stage(helper, DEMENTOR_Y);
        Runnable release = WizardTestSupport.leaseModule(Module.AZKABAN);
        ServerPlayer near = WizardTestSupport.placeMockPlayer(helper, "wandb-dementor-near", GameType.SURVIVAL);
        ServerPlayer middle = WizardTestSupport.placeMockPlayer(helper, "wandb-dementor-middle", GameType.SURVIVAL);
        ServerPlayer far = WizardTestSupport.placeMockPlayer(helper, "wandb-dementor-far", GameType.SURVIVAL);
        ServerPlayer builder = watcher(helper, "wandb-dementor-creative", DEMENTOR_Y, 7, 0);
        place(helper, near, DEMENTOR_Y, 1, 1);      // ~1.4 from the first: drain
        place(helper, middle, DEMENTOR_Y, 4, 4);    // ~5.7: fear
        place(helper, far, DEMENTOR_Y, 7, 8);       // ~10.6: cold
        List<DementorEntity> dementors = new ArrayList<>();
        List<PatronusEntity> patroni = new ArrayList<>();
        List<Cow> cows = new ArrayList<>();
        var chill = ModEffects.DEMENTOR_CHILL;

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(DEMENTOR_Y), max(DEMENTOR_Y)))
                .thenExecute(() -> {
                    DementorEntity first = helper.spawn(ModEntities.DEMENTOR.get(), new BlockPos(0, DEMENTOR_Y, 0));
                    first.setNoAi(true);
                    dementors.add(first);
                    Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, DEMENTOR_Y, 3));
                    cow.setNoAi(true);
                    cows.add(cow);
                })
                .thenWaitUntil(() -> check(helper, near.hasEffect(chill), () -> "no chill on a player at arm's length"))
                .thenIdle(2)
                .thenExecute(() -> {
                    DementorEntity first = dementors.get(0);
                    check(helper, amp(near) == 2 && amp(middle) == 1 && amp(far) == 0,
                            () -> "one dementor: bands were " + amp(near) + "/" + amp(middle) + "/" + amp(far) + ", expected 2/1/0");
                    check(helper, !builder.hasEffect(chill), () -> "a creative player was chilled");
                    check(helper, first.sensed() == near, () -> "it did not feel the nearest player first: " + first.sensed());
                    check(helper, cows.get(0).hasEffect(chill), () -> "an animal near it felt nothing");
                    // Two more close by: a swarm deepens every band by one, capped at three.
                    for (BlockPos at : List.of(new BlockPos(0, DEMENTOR_Y, 1), new BlockPos(1, DEMENTOR_Y, 0))) {
                        DementorEntity more = helper.spawn(ModEntities.DEMENTOR.get(), at);
                        more.setNoAi(true);
                        dementors.add(more);
                    }
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    check(helper, amp(near) == 3 && amp(middle) == 2 && amp(far) == 1,
                            () -> "a swarm: bands were " + amp(near) + "/" + amp(middle) + "/" + amp(far) + ", expected 3/2/1");
                    check(helper, dementors.stream().noneMatch(d -> d.hasEffect(chill)), () -> "a dementor chilled a dementor");
                })
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    DementorEntity first = dementors.get(0);
                    DementorEntity second = dementors.get(1);
                    check(helper, !DementorKissGoal.canBeKissed(middle),
                            () -> "a healthy player at the edge of the drain could be Kissed");
                    near.setHealth(6.0f);
                    check(helper, DementorKissGoal.canBeKissed(near), () -> "a weakened player under a swarm could not be Kissed");
                    // A victim claimed by one is not another's.
                    second.claimKiss(near);
                    check(helper, first.kissClaimedByAnother(near), () -> "two dementors could claim one victim");
                    second.claimKiss(null);
                    check(helper, !first.kissClaimedByAnother(near), () -> "a released claim still held");

                    check(helper, DementorKissGoal.kiss(level, first, near), () -> "the Kiss did not land");
                    float after = near.getHealth();
                    check(helper, near.hasEffect(ModEffects.SOUL_DRAINED), () -> "the Kiss took no soul");
                    check(helper, after < 6.0f, () -> "the Kiss did no harm");
                    check(helper, !DementorKissGoal.kiss(level, second, near), () -> "a soul was taken twice");
                    check(helper, near.getHealth() == after, () -> "a second Kiss still hurt");
                    // Nothing gives it back.
                    near.removeAllEffects();
                    near.removeEffect(ModEffects.SOUL_DRAINED);
                    check(helper, near.hasEffect(ModEffects.SOUL_DRAINED), () -> "milk or a command undid the Kiss");

                    // Blows do nothing; only dissipation gets through.
                    check(helper, !first.hurtServer(level, level.damageSources().playerAttack(middle), 1000.0f)
                                    && first.getHealth() == first.getMaxHealth(),
                            () -> "a blow harmed a dementor");

                    second.startKissCooldown(DementorEntity.KISS_COOLDOWN);
                    DementorEntity saved = roundTrip(helper, second);
                    check(helper, saved != null && saved.kissCooldown() >= DementorEntity.KISS_COOLDOWN - 2,
                            () -> "a dementor's Kiss cooldown did not survive a save");
                })
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    DementorEntity first = dementors.get(0);
                    // A corporeal Patronus wards a wide sphere, and the dementor inside it flees.
                    PatronusEntity.trySpawn(level, middle, 60.0f, 1.0f, "minecraft:wolf");
                    PatronusEntity stag = level.getEntitiesOfClass(PatronusEntity.class, middle.getBoundingBox().inflate(4)).get(0);
                    patroni.add(stag);
                    check(helper, stag.isCorporeal(), () -> "a power-60 Patronus was not corporeal");
                    check(helper, PatronusDetection.isWarded(level, middle.position())
                                    && PatronusDetection.isWarded(level, far.position()),
                            () -> "a corporeal Patronus did not ward those near it");
                    check(helper, !first.canFeel(middle), () -> "a dementor still felt a warded player");
                    first.setNoAi(false);
                })
                .thenWaitUntil(() -> check(helper, dementors.get(0).state() == DementorEntity.State.REPELLED,
                        () -> "a dementor inside a Patronus's ward did not flee: " + dementors.get(0).state()))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    patroni.forEach(PatronusEntity::discard);
                    // A mist wards only the few blocks around itself: its caster, not someone across the stage.
                    PatronusEntity.trySpawn(level, far, 10.0f, 1.0f, "minecraft:wolf");
                    PatronusEntity mist = level.getEntitiesOfClass(PatronusEntity.class, far.getBoundingBox().inflate(4)).get(0);
                    patroni.add(mist);
                    check(helper, !mist.isCorporeal(), () -> "a power-10 Patronus took a form");
                    check(helper, PatronusDetection.isWarded(level, far.position()), () -> "a mist did not shield its caster");
                    check(helper, !PatronusDetection.isWarded(level, near.position()), () -> "a mist warded someone ten blocks away");

                    DementorEntity third = dementors.get(2);
                    check(helper, third.hurtServer(level, level.damageSources().source(AzkabanDamageTypes.DEMENTOR_DISSIPATE),
                            Float.MAX_VALUE), () -> "dissipation did not get through");
                    check(helper, third.isDeadOrDying() && third.state() == DementorEntity.State.DISSIPATING,
                            () -> "a dissipated dementor did not unravel: " + third.state());
                })
                .thenWaitUntil(() -> check(helper, dementors.get(2).isRemoved(), () -> "a dissipated dementor lingered"))
                .thenExecute(() -> {
                    dementors.forEach(DementorEntity::discard);
                    patroni.forEach(PatronusEntity::discard);
                    cows.forEach(Cow::discard);
                    retire(helper, near);
                    retire(helper, middle);
                    retire(helper, far);
                    retire(helper, builder);
                    release.run();
                })
                .thenSucceed();
    }

    private static int amp(ServerPlayer player) {
        var effect = player.getEffect(ModEffects.DEMENTOR_CHILL);
        return effect == null ? -1 : effect.getAmplifier();
    }

    // ── the thestral ──────────────────────────────────────────────────────────────────────────────

    private static void thestralIsRealToEveryone(GameTestHelper helper) {
        stage(helper, THESTRAL_Y);
        ServerPlayer witness = WizardTestSupport.placeMockPlayer(helper, "wandb-creature-thestral-witness", GameType.SURVIVAL);
        place(helper, witness, THESTRAL_Y, 1, 1);
        String flag = ThestralEntity.WITNESSED_DEATH_FLAG;
        // Joins after the death: the stage is smaller than the witness radius, so nobody on it could have missed it.
        ServerPlayer[] late = new ServerPlayer[1];

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(THESTRAL_Y), max(THESTRAL_Y)))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    check(helper, !PlayerAbilityHelper.hasAbilityFlag(witness, flag),
                            () -> "a fresh test player already sees thestrals");
                    // An animal's death is not "seeing death".
                    var cow = helper.spawn(EntityType.COW, new BlockPos(2, THESTRAL_Y, 2));
                    cow.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
                    check(helper, !PlayerAbilityHelper.hasAbilityFlag(witness, flag),
                            () -> "watching a cow die granted thestral sight");
                    // A person's death, seen close, is.
                    var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, THESTRAL_Y, 1));
                    villager.setNoAi(true);
                    villager.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
                    check(helper, PlayerAbilityHelper.hasAbilityFlag(witness, flag),
                            () -> "seeing a villager die did not grant thestral sight");
                })
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    ServerPlayer unseeing = WizardTestSupport.placeMockPlayer(helper, "wandb-creature-thestral-unseeing",
                            GameType.SURVIVAL);
                    place(helper, unseeing, THESTRAL_Y, 6, 7);
                    late[0] = unseeing;
                    ThestralEntity thestral = helper.spawn(ModEntities.THESTRAL.get(), new BlockPos(5, THESTRAL_Y, 5));
                    thestral.setNoAi(true);
                    check(helper, !thestral.isInvisible(),
                            () -> "a thestral is invisible to the server: sight is a client-side matter");
                    // Someone who cannot see it can still feed it and be carried — Ron rode one.
                    check(helper, !PlayerAbilityHelper.hasAbilityFlag(unseeing, flag), () -> "a late arrival has sight");
                    unseeing.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF));
                    thestral.interact(unseeing, InteractionHand.MAIN_HAND);
                    check(helper, thestral.bondLevel() > 0, () -> "an unseeing player could not feed a thestral");
                    unseeing.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    thestral.bondState().setLevel(ThestralEntity.RIDE_BOND - 1, 100);
                    thestral.interact(unseeing, InteractionHand.MAIN_HAND);
                    check(helper, !unseeing.isPassenger(), () -> "a thestral carried a stranger it did not trust");
                    thestral.bondState().setLevel(ThestralEntity.RIDE_BOND, 100);
                    thestral.interact(unseeing, InteractionHand.MAIN_HAND);
                    check(helper, unseeing.getVehicle() == thestral && thestral.getControllingPassenger() == unseeing,
                            () -> "an unseeing player could not ride a trusting thestral");

                    ThestralEntity saved = roundTrip(helper, thestral);
                    check(helper, saved != null && saved.bondLevel() == ThestralEntity.RIDE_BOND,
                            () -> "a thestral's trust did not survive a save");

                    // Struck, it throws its rider and turns on whoever struck it.
                    thestral.hurtServer(level, level.damageSources().playerAttack(witness), 1.0f);
                    check(helper, !unseeing.isPassenger(), () -> "a struck thestral kept its rider");
                    check(helper, level.getEntitiesOfClass(ThestralEntity.class, thestral.getBoundingBox().inflate(8)).size() == 1,
                            () -> "more than one thestral on the stage");
                })
                .thenExecute(() -> {
                    retire(helper, witness);
                    if (late[0] != null) {
                        retire(helper, late[0]);
                    }
                })
                .thenSucceed();
    }

    // ── the hippogriff ────────────────────────────────────────────────────────────────────────────

    private static void hippogriffAnswersABow(GameTestHelper helper) {
        stage(helper, HIPPOGRIFF_Y);
        ServerPlayer friend = WizardTestSupport.placeMockPlayer(helper, "wandb-creature-hippogriff-friend", GameType.SURVIVAL);
        ServerPlayer stranger = WizardTestSupport.placeMockPlayer(helper, "wandb-creature-hippogriff-stranger", GameType.SURVIVAL);
        place(helper, friend, HIPPOGRIFF_Y, 3, 0);
        place(helper, stranger, HIPPOGRIFF_Y, 6, 5);
        HippogriffEntity hippogriff = (HippogriffEntity) still(helper, "hippogriff", HIPPOGRIFF_Y, 3, 5);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(HIPPOGRIFF_Y), max(HIPPOGRIFF_Y)))
                .thenExecute(() -> {
                    lookAt(friend, hippogriff);
                    friend.setShiftKeyDown(true);   // the bow
                    lookAt(stranger, hippogriff);   // walked straight up to it, no bow
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    check(helper, hippogriff.respects(friend), () -> "a hippogriff did not bow back to a bow");
                    check(helper, !hippogriff.respects(stranger), () -> "a hippogriff respected someone who never bowed");
                    check(helper, hippogriff.getTarget() == stranger,
                            () -> "a stranger who crowded it after the warning was not attacked: " + hippogriff.getTarget());
                    hippogriff.setTarget(null);

                    // A stranger cannot touch, feed or ride it; a respected friend can feed it.
                    stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RABBIT));
                    hippogriff.interact(stranger, InteractionHand.MAIN_HAND);
                    check(helper, hippogriff.bondState().level() == 0 && stranger.getMainHandItem().getCount() == 1,
                            () -> "a hippogriff took food from someone it had not bowed to");
                    friend.setShiftKeyDown(false);
                    friend.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RABBIT));
                    hippogriff.interact(friend, InteractionHand.MAIN_HAND);
                    check(helper, hippogriff.bondState().level() > 0
                                    && friend.getUUID().equals(hippogriff.bondState().ownerUUID()),
                            () -> "a hippogriff that bowed to its friend would not be fed by them");

                    // Riding needs trust as well as courtesy.
                    friend.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    hippogriff.bondState().setLevel(WildlifeRules.HIPPOGRIFF_RIDE_BOND - 1, 100);
                    hippogriff.interact(friend, InteractionHand.MAIN_HAND);
                    check(helper, !friend.isPassenger(), () -> "a hippogriff let an untrusted friend ride");
                    hippogriff.bondState().setLevel(WildlifeRules.HIPPOGRIFF_RIDE_BOND, 100);
                    hippogriff.interact(friend, InteractionHand.MAIN_HAND);
                    check(helper, friend.getVehicle() == hippogriff && hippogriff.getControllingPassenger() == friend,
                            () -> "a trusted friend could not ride");

                    // Coat and courtesy survive a save.
                    hippogriff.setCoat(HippogriffEntity.Coat.CHESTNUT);
                    HippogriffEntity saved = roundTrip(helper, hippogriff);
                    check(helper, saved != null && saved.coat() == HippogriffEntity.Coat.CHESTNUT && saved.respects(friend)
                                    && !saved.respects(stranger),
                            () -> "coat or respect did not survive a save");

                    // Striking it throws the rider and ends the courtesy.
                    hippogriff.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(friend), 1.0f);
                    check(helper, !friend.isPassenger() && !hippogriff.respects(friend),
                            () -> "striking a hippogriff kept its respect, or its rider");
                })
                .thenExecute(() -> {
                    retire(helper, friend);
                    retire(helper, stranger);
                })
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

    private static void kelpieLuresGripsAndLetsGo(GameTestHelper helper) {
        stage(helper, KELPIE_Y);
        // A pool two blocks deep, flush with the floor, walled in so it stays put on the floating stage.
        for (int x = 3; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                boolean edge = x == 3 || x == 8 || z == -1 || z == 8;
                helper.setBlock(new BlockPos(x, KELPIE_Y - 3, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, KELPIE_Y - 2, z), edge ? Blocks.STONE : Blocks.WATER);
                helper.setBlock(new BlockPos(x, KELPIE_Y - 1, z), edge ? Blocks.STONE : Blocks.WATER);
            }
        }
        ServerPlayer rider = WizardTestSupport.placeMockPlayer(helper, "wandb-kelpie-rider", GameType.SURVIVAL);
        ServerPlayer swimmer = WizardTestSupport.placeMockPlayer(helper, "wandb-kelpie-swimmer", GameType.SURVIVAL);
        ServerPlayer tamer = WizardTestSupport.placeMockPlayer(helper, "wandb-kelpie-tamer", GameType.SURVIVAL);
        place(helper, rider, KELPIE_Y, 1, 2);
        place(helper, swimmer, KELPIE_Y - 2, 5, 4);
        place(helper, tamer, KELPIE_Y, 0, 7);
        KelpieEntity kelpie = (KelpieEntity) helper.spawn(ModCreatures.ENTITIES.get("kelpie").get(), new BlockPos(1, KELPIE_Y, 4));
        kelpie.setNoAi(true);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(KELPIE_Y), max(KELPIE_Y)))
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    check(helper, kelpie.isDisguised() && kelpie.hasHome(), () -> "a fresh kelpie was not a horse by its water");

                    // The horse moment: on, and off again freely — a chance to notice.
                    kelpie.interact(rider, InteractionHand.MAIN_HAND);
                    check(helper, rider.getVehicle() == kelpie, () -> "a disguised kelpie would not be mounted");
                    KelpieLureGoal lure = new KelpieLureGoal(kelpie, 60, 1.3, 16);
                    check(helper, lure.canUse(), () -> "the lure did not take its rider");
                    check(helper, helper.absolutePos(new BlockPos(4, KELPIE_Y - 1, 4)).distSqr(lure.findWater(level)) < 40,
                            () -> "the kelpie could not find its pool: " + lure.findWater(level));
                    lure.start();
                    for (int i = 0; i < 59; i++) {
                        lure.tick();
                    }
                    check(helper, kelpie.isDisguised() && !kelpie.isGripping(), () -> "it revealed itself early");
                    rider.stopRiding();
                    check(helper, rider.getVehicle() == null, () -> "a rider could not get off a horse that was only a horse");
                    kelpie.interact(rider, InteractionHand.MAIN_HAND);

                    // The grip: revealed, and no getting off.
                    for (int i = 0; i < 60; i++) {
                        lure.tick();
                    }
                    check(helper, kelpie.isGripping() && !kelpie.isDisguised(), () -> "it never gripped its rider");
                    rider.stopRiding();
                    check(helper, rider.getVehicle() == kelpie, () -> "a gripped rider simply got off");

                    // Drowning is the water's doing: a head under water loses its breath fast; on land, nothing.
                    int air = swimmer.getAirSupply();
                    KelpieEntity.drown(swimmer);
                    check(helper, swimmer.getAirSupply() < air, () -> "a head under water kept its breath");
                    int dry = rider.getAirSupply();
                    KelpieEntity.drown(rider);
                    check(helper, rider.getAirSupply() == dry, () -> "a rider on dry land was drowned");
                    swimmer.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200));
                    int breathing = swimmer.getAirSupply();
                    KelpieEntity.drown(swimmer);
                    check(helper, swimmer.getAirSupply() == breathing, () -> "a water-breather was drowned");

                    // Fighting back: a hard enough blow and it lets go, and will not lure again for a while.
                    kelpie.hurtServer(level, level.damageSources().playerAttack(rider), KelpieEntity.BREAK_FREE_DAMAGE + 1);
                    lure.tick();
                    check(helper, !kelpie.isGripping() && rider.getVehicle() == null && kelpie.lureCooldown() > 0,
                            () -> "a struck kelpie kept its grip (riding " + (rider.getVehicle() != null) + ")");
                    kelpie.interact(rider, InteractionHand.MAIN_HAND);
                    check(helper, rider.getVehicle() == null, () -> "a revealed kelpie let someone straight back on");

                    // The grip never lasts past its time.
                    rider.startRiding(kelpie, true, true);
                    kelpie.beginGrip(rider);
                    int held = 0;
                    while (kelpie.tickGrip() && held < KelpieEntity.GRIP_TICKS + 5) {
                        held++;
                    }
                    int heldFor = held;
                    check(helper, !kelpie.isGripping() && rider.getVehicle() == null && heldFor < KelpieEntity.GRIP_TICKS + 5,
                            () -> "the grip outlasted its time: " + heldFor);

                    // The bridle: refused while it has its strength, taken once it is subdued.
                    tamer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
                    kelpie.interact(tamer, InteractionHand.MAIN_HAND);
                    check(helper, !kelpie.isBridled(), () -> "a kelpie in its strength took a bridle");
                    kelpie.addEffect(new MobEffectInstance(ModEffects.STUPEFY, 100));
                    kelpie.interact(tamer, InteractionHand.MAIN_HAND);
                    check(helper, kelpie.isBridled(), () -> "a Stupefied kelpie would not take a bridle");
                    kelpie.removeEffect(ModEffects.STUPEFY);
                    kelpie.interact(tamer, InteractionHand.MAIN_HAND);
                    check(helper, tamer.getVehicle() == kelpie && kelpie.getControllingPassenger() == tamer,
                            () -> "a bridled kelpie could not be ridden and steered");
                    check(helper, !new KelpieLureGoal(kelpie, 60, 1.3, 16).canUse(), () -> "a bridled kelpie still lured");
                    tamer.stopRiding();

                    kelpie.setCoat(KelpieEntity.Coat.GREEN_BLACK);
                    KelpieEntity saved = roundTrip(helper, kelpie);
                    check(helper, saved != null && saved.isBridled() && saved.coat() == KelpieEntity.Coat.GREEN_BLACK
                                    && saved.lureCooldown() > 0 && !saved.isGripping(),
                            () -> "a kelpie's bridle, coat or cooldown did not survive a save (or a grip did)");
                })
                .thenExecute(() -> {
                    kelpie.discard();
                    retire(helper, rider);
                    retire(helper, swimmer);
                    retire(helper, tamer);
                })
                .thenSucceed();
    }

    private static void bowtruckleHidesAndPicksLocks(GameTestHelper helper) {
        stage(helper, BOWTRUCKLE_HIDE_Y);
        for (int dy = 0; dy < 4; dy++) {
            helper.setBlock(new BlockPos(3, BOWTRUCKLE_HIDE_Y + dy, 4), Blocks.OAK_LOG);
        }
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "wandb-bowtruckle-owner", GameType.SURVIVAL);
        ServerPlayer stranger = WizardTestSupport.placeMockPlayer(helper, "wandb-bowtruckle-stranger", GameType.SURVIVAL);
        ServerPlayer feeder = WizardTestSupport.placeMockPlayer(helper, "wandb-bowtruckle-feeder", GameType.SURVIVAL);
        place(helper, owner, BOWTRUCKLE_HIDE_Y, 2, 2);
        place(helper, stranger, BOWTRUCKLE_HIDE_Y, 6, 7);
        place(helper, feeder, BOWTRUCKLE_HIDE_Y, 5, 2);
        BowtruckleEntity bowtruckle = helper.spawn(ModEntities.BOWTRUCKLE.get(), new BlockPos(2, BOWTRUCKLE_HIDE_Y, 4));
        bowtruckle.setNoAi(true);
        BlockPos iron = new BlockPos(0, BOWTRUCKLE_HIDE_Y, 7);
        BlockPos sealed = new BlockPos(7, BOWTRUCKLE_HIDE_Y, 0);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BOWTRUCKLE_HIDE_Y), max(BOWTRUCKLE_HIDE_Y)))
                .thenIdle(2)
                .thenExecute(() -> check(helper, bowtruckle.homeTree() != null, () -> "no home tree beside a trunk"))
                // Still against bark, it fades into the tree — faint, never invisible.
                .thenWaitUntil(() -> check(helper, bowtruckle.isCamouflaged(), () -> "a still bowtruckle at the bark never camouflaged"))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    check(helper, !bowtruckle.isInvisible(), () -> "camouflage made it invisible");
                    bowtruckle.bondState().setOwner(owner.getUUID());
                    // Its own person is nearest, yet the one it notices is a stranger.
                    Player noticed = bowtruckle.nearestStranger(BowtruckleHideGoal.STARTLE_RANGE);
                    check(helper, noticed != null && noticed != owner,
                            () -> "it noticed " + noticed + " — nobody, or its own person — instead of a stranger");
                    check(helper, new BowtruckleHideGoal(bowtruckle).canUse(), () -> "a startled bowtruckle had nowhere to hide");
                    bowtruckle.startled();
                    check(helper, !bowtruckle.isCamouflaged(), () -> "a startled bowtruckle stayed camouflaged");

                    // Locks: an iron trapdoor it can pick for its trusted person; a Colloportus seal it cannot.
                    helper.setBlock(iron, Blocks.IRON_TRAPDOOR);
                    helper.setBlock(sealed, Blocks.IRON_TRAPDOOR);
                    ColloportusLockStore.lock(level, helper.absolutePos(sealed));
                    check(helper, !BowtruckleLockpickGoal.pickable(level, helper.absolutePos(sealed)),
                            () -> "a bowtruckle could pick a magical seal");
                    check(helper, !bowtruckle.askToPickLock(owner), () -> "an untrusting bowtruckle picked a lock");
                    bowtruckle.bondState().setLevel(BowtruckleEntity.LOCKPICK_BOND, 100);
                    // Trust is its person's, not anyone's: a stranger still cannot set it to work.
                    check(helper, !bowtruckle.askToPickLock(stranger), () -> "a stranger set a bowtruckle to pick a lock");
                    check(helper, bowtruckle.askToPickLock(owner) && helper.absolutePos(iron).equals(bowtruckle.lockJob()),
                            () -> "a trusting bowtruckle would not pick the iron lock: " + bowtruckle.lockJob());
                    BlockPos lockAt = helper.absolutePos(iron);
                    bowtruckle.snapTo(lockAt.getX() + 0.5, lockAt.getY(), lockAt.getZ() - 0.5, 0f, 0f);
                    BowtruckleLockpickGoal pick = new BowtruckleLockpickGoal(bowtruckle);
                    pick.start();
                    for (int i = 0; i < BowtruckleLockpickGoal.PICK_TICKS; i++) {
                        pick.tick();
                    }
                    check(helper, level.getBlockState(lockAt).getValue(BlockStateProperties.OPEN),
                            () -> "the picked iron trapdoor did not open");
                    check(helper, bowtruckle.lockJob() == null, () -> "a finished lock job lingered");
                    check(helper, !level.getBlockState(helper.absolutePos(sealed)).getValue(BlockStateProperties.OPEN),
                            () -> "the sealed trapdoor opened");
                    ColloportusLockStore.unlock(level, helper.absolutePos(sealed));
                    bowtruckle.snapTo(helper.absolutePos(new BlockPos(2, BOWTRUCKLE_HIDE_Y, 4)).getBottomCenter());

                    // Canon's woodlouse: whoever just fed it may take wood; anyone else is set on.
                    feeder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                    bowtruckle.interact(feeder, InteractionHand.MAIN_HAND);
                    feeder.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, BOWTRUCKLE_HIDE_Y + 3, 4)));
                    check(helper, !bowtruckle.isDefending(), () -> "a bowtruckle set on the person who had just fed it");
                    stranger.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, BOWTRUCKLE_HIDE_Y + 2, 4)));
                    check(helper, bowtruckle.isDefending() && stranger.getUUID().equals(bowtruckle.angryAt()),
                            () -> "a bowtruckle let a stranger cut its tree");
                })
                .thenExecute(() -> {
                    bowtruckle.discard();
                    retire(helper, owner);
                    retire(helper, stranger);
                    retire(helper, feeder);
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

    private static void nifflerDigsPocketsAndHoards(GameTestHelper helper) {
        stage(helper, NIFFLER_DIG_Y);
        List<Entity> spawned = new ArrayList<>();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(NIFFLER_DIG_Y), max(NIFFLER_DIG_Y)))
                .thenExecute(() -> {
                    NifflerEntity pocket = helper.spawn(ModEntities.NIFFLER.get(), new BlockPos(1, NIFFLER_DIG_Y, 1));
                    pocket.setNoAi(true);
                    NifflerEntity digger = helper.spawn(ModEntities.NIFFLER.get(), new BlockPos(3, NIFFLER_DIG_Y, 3));
                    digger.setNoAi(true);
                    spawned.add(pocket);
                    spawned.add(digger);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    NifflerEntity pocket = (NifflerEntity) spawned.get(0);
                    NifflerEntity digger = (NifflerEntity) spawned.get(1);
                    check(helper, pocket.hasHome(), () -> "a wild niffler had no burrow");

                    // Only what fits: a nearly full pouch takes four of sixty-four ingots; the rest stay on the ground.
                    for (int i = 0; i < pocket.getPouch().getContainerSize() - 1; i++) {
                        pocket.getPouch().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                    }
                    pocket.getPouch().setItem(pocket.getPouch().getContainerSize() - 1, new ItemStack(Items.GOLD_INGOT, 60));
                    ItemEntity heap = drop(helper, Items.GOLD_INGOT, NIFFLER_DIG_Y, 1, 2);
                    heap.setItem(new ItemStack(Items.GOLD_INGOT, 64));
                    int taken = pocket.pickUp(heap);
                    check(helper, taken == 4 && heap.isAlive() && heap.getItem().getCount() == 60,
                            () -> "took " + taken + ", left " + heap.getItem().getCount() + " (alive " + heap.isAlive() + ")");
                    check(helper, pocket.pickUp(heap) == 0, () -> "a full pouch took more");
                    heap.discard();
                    check(helper, pocket.pickUp(heap) == 0, () -> "a niffler took from an item that was already gone");

                    // A fresh drop is not yet lying about, and junk is not treasure.
                    ItemEntity fresh = drop(helper, Items.DIAMOND, NIFFLER_DIG_Y, 4, 1);
                    fresh.setDefaultPickUpDelay();
                    ItemEntity junk = drop(helper, Items.STICK, NIFFLER_DIG_Y, 3, 1);
                    ItemEntity wanted = new NifflerSeekShinyItemGoal(digger).findPreferred();
                    check(helper, wanted == null, () -> "a niffler went for " + (wanted == null ? "" : wanted.getItem()));
                    fresh.discard();
                    junk.discard();

                    // Ore open to the air: planned, and mined for real — the block gone, its drops in the pouch.
                    BlockPos ore = new BlockPos(5, NIFFLER_DIG_Y - 1, 5);
                    helper.setBlock(ore, Blocks.GOLD_ORE);
                    NifflerSeekShinyBlockGoal dig = new NifflerSeekShinyBlockGoal(digger);
                    check(helper, dig.canUse() && dig.plannedDig().contains(helper.absolutePos(ore)),
                            () -> "an exposed gold ore was not planned: " + dig.plannedDig());
                    BlockPos oreAt = helper.absolutePos(ore);
                    digger.mine(level, oreAt, level.getBlockState(oreAt));
                    check(helper, level.getBlockState(oreAt).isAir(), () -> "the mined ore is still there");
                    check(helper, digger.getPouch().countItem(Items.RAW_GOLD) >= 1,
                            () -> "the ore's gold did not go into the pouch");
                    check(helper, itemsNear(helper, NIFFLER_DIG_Y, Items.RAW_GOLD).isEmpty(),
                            () -> "mined gold was left lying about instead of pocketed");
                    helper.setBlock(ore, Blocks.STONE);

                    // Buried under loose earth: it burrows down; under stone, it cannot reach.
                    BlockPos buried = new BlockPos(6, NIFFLER_DIG_Y - 1, 1);
                    // The stage floats: close the ore in from below too, or it is open to the air underneath.
                    helper.setBlock(buried.below(), Blocks.STONE);
                    helper.setBlock(buried, Blocks.GOLD_ORE);
                    helper.setBlock(buried.above(), Blocks.DIRT);
                    NifflerSeekShinyBlockGoal burrow = new NifflerSeekShinyBlockGoal(digger);
                    check(helper, burrow.canUse() && burrow.plannedDig().size() == 2
                                    && burrow.plannedDig().peekFirst().equals(helper.absolutePos(buried.above()))
                                    && burrow.plannedDig().peekLast().equals(helper.absolutePos(buried)),
                            () -> "a niffler did not burrow through earth to buried ore: " + burrow.plannedDig());
                    helper.setBlock(buried.above(), Blocks.STONE);
                    check(helper, !new NifflerSeekShinyBlockGoal(digger).canUse(), () -> "a niffler dug through stone");

                    // A gold block is somebody's build, not a deposit.
                    helper.setBlock(buried, Blocks.GOLD_BLOCK);
                    helper.setBlock(buried.above(), Blocks.AIR);
                    check(helper, !new NifflerSeekShinyBlockGoal(digger).canUse(), () -> "a niffler went for a gold block");

                    // With mobGriefing off it digs nothing.
                    helper.setBlock(buried, Blocks.GOLD_ORE);
                    boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
                    level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
                    boolean dugAnyway = new NifflerSeekShinyBlockGoal(digger).canUse();
                    level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
                    check(helper, !dugAnyway, () -> "a niffler dug with mobGriefing off");
                    helper.setBlock(buried, Blocks.STONE);

                    // Enough in the pouch and it heads home to hoard.
                    check(helper, new NifflerHoardGoal(pocket).canUse(), () -> "a niffler with a full pouch never hoarded");

                    // The pouch and the coat survive a save.
                    pocket.setCoat(NifflerEntity.Coat.PALE);
                    NifflerEntity saved = roundTrip(helper, pocket);
                    check(helper, saved != null && saved.treasureCount() == pocket.treasureCount()
                                    && saved.coat() == NifflerEntity.Coat.PALE,
                            () -> "a niffler's pouch or coat did not survive a save");

                    // A baby that grows up keeps what it had.
                    BabyNifflerEntity baby = helper.spawn(ModEntities.BABY_NIFFLER.get(), new BlockPos(6, NIFFLER_DIG_Y, 6));
                    baby.setNoAi(true);
                    baby.setCoat(NifflerEntity.Coat.GREY);
                    baby.getPouch().addItem(new ItemStack(Items.GOLD_NUGGET, 7));
                    baby.growIntoAdult();
                    List<NifflerEntity> grown = level.getEntitiesOfClass(NifflerEntity.class, baby.getBoundingBox().inflate(1),
                            n -> !n.isBaby() && n.isAlive());
                    check(helper, grown.size() == 1 && grown.get(0).getPouch().countItem(Items.GOLD_NUGGET) == 7
                                    && grown.get(0).coat() == NifflerEntity.Coat.GREY,
                            () -> "a niffler that grew up lost its pouch or its coat");
                    spawned.addAll(grown);

                    // And its death spills the pouch — once.
                    digger.hurtServer(level, level.damageSources().generic(), 1000f);
                    check(helper, !itemsNear(helper, NIFFLER_DIG_Y, Items.RAW_GOLD).isEmpty(),
                            () -> "a dead niffler's pouch vanished");
                })
                .thenExecute(() -> {
                    spawned.forEach(Entity::discard);
                    itemsNear(helper, NIFFLER_DIG_Y, null).forEach(Entity::discard);
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

    static BlockPos min(int y) {
        return new BlockPos(-1, y - 1, -1);
    }

    static BlockPos max(int y) {
        return new BlockPos(7, y + 5, 8);
    }

    static void stage(GameTestHelper helper, int y) {
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

    static void place(GameTestHelper helper, ServerPlayer player, int y, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        player.setNoGravity(true);
    }

    static GenericBeastEntity still(GameTestHelper helper, String id, int y, int x, int z) {
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

    static void lookAt(ServerPlayer player, Entity target) {
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

    static Identifier page(String id) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id);
    }

    static DiscoveryTier tier(ServerPlayer player, Identifier page) {
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
    static <T extends Entity> T roundTrip(GameTestHelper helper, T entity) {
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
