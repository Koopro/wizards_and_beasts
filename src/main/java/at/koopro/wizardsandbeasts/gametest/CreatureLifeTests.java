package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyNeed;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyPersonality;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyProfile;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyProfileRegistry;
import at.koopro.wizardsandbeasts.creature.ecology.EcologySensing;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyState;
import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.creature.profile.HasActivity;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Daily life against the real world: drinking, grazing, sleeping, weather, hazards, kin, noise, remains, giving up,
 * surviving a save, showing it, and the cost of it all with forty creatures about.
 *
 * <p>Every scenario runs alone (creatures sense up to 32 blocks) and puts its creature's ecology in place with
 * {@link EcologyProfileRegistry#override}, with every personality axis pinned at 0.5 — so a result never depends
 * on the shipped data of the day or on the UUID the creature happened to get. Everything a scenario changes (the
 * override, the weather, the time, its pen, its creatures) is undone through {@link TestCleanup}, so a scenario that
 * fails half-way leaves nothing behind for the next one.
 */
public final class CreatureLifeTests {

    private static final int Y = 6;
    /*
     * Each scenario has a species of its own, used by no other GameTest: scenarios run side by side, and a profile
     * override is per species. Plain, ability-free walkers where the scenario moves.
     */
    private static final String DRINKER = "flobberworm";
    private static final String GRAZER = "toad";
    private static final String QUITTER = "puffskein";
    private static final String SLEEPER = "pygmy_puff";
    private static final String SHELTERER = "mackled_malaclaw";
    private static final String BURNT = "murtlap";
    private static final String LONER = "knarl";
    private static final String STARTLED = "porlock";
    private static final String NOSY = "diricawl";
    private static final String SHY = "gnome";
    private static final String SCAVENGER = "salamander";
    private static final String SAVED = "jobberknoll";
    private static final String HERD = "qilin";
    /** Every undo runs by this deadline even if its scenario failed: a little past the scenario timeout. */
    private static final int UNDO_BY = 820;

    private CreatureLifeTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("ecology_life_drinks_at_water", "ecology: a thirsty creature finds the pond, walks to it and drinks",
                CreatureLifeTests::drinksAtWater);
        tests.addAlone("ecology_life_grazes", "ecology: a hungry grazer finds grass and eats until sated, and shows it",
                CreatureLifeTests::grazes);
        tests.addAlone("ecology_life_gives_up_on_unreachable_water",
                "ecology: water it cannot reach is given up after the timeout and not tried again at once",
                CreatureLifeTests::givesUpOnUnreachableWater);
        tests.addAlone("ecology_life_sleeps_by_its_rhythm", "ecology: a diurnal creature sleeps at night and wakes by day",
                CreatureLifeTests::sleepsByRhythm);
        tests.addAlone("ecology_life_shelters_from_rain",
                "ecology: a weather-shy creature takes cover in rain and stays under it until the rain stops",
                CreatureLifeTests::sheltersFromRain);
        tests.addAlone("ecology_life_flees_fire", "ecology: a burning creature gets out, whatever else it was doing",
                CreatureLifeTests::fleesFire);
        tests.addAlone("ecology_life_solitary_keeps_distance", "ecology: a solitary creature moves off from its own kind",
                CreatureLifeTests::solitaryKeepsDistance);
        tests.addAlone("ecology_life_noise_scatters_and_draws",
                "ecology: an explosion sends prey running and draws the curious; a second one at once is rate-limited",
                CreatureLifeTests::noiseScattersAndDraws);
        tests.addAlone("ecology_life_running_person_frightens_the_shy",
                "ecology: a person running out of a shy creature's sight is heard and sends it off; walking is not",
                CreatureLifeTests::runningPersonFrightensShy);
        tests.addAlone("ecology_life_scavenger_visits_a_kill", "ecology: a scavenger goes to where something died",
                CreatureLifeTests::scavengerVisitsKill);
        tests.addAlone("ecology_life_state_survives_a_save", "ecology: home and needs survive a save and load",
                CreatureLifeTests::stateSurvivesSave);
        tests.addAlone("ecology_life_forty_creatures_within_budget",
                "ecology: forty living creatures look, search and decide within their schedule's budget",
                CreatureLifeTests::fortyWithinBudget, 800);
    }

    // ── set-up ──────────────────────────────────────────────────────────────────────────────────

    private static final Map<EcologyPersonality.Axis, EcologyPersonality.Range> NEUTRAL = neutral();

    private static Map<EcologyPersonality.Axis, EcologyPersonality.Range> neutral() {
        Map<EcologyPersonality.Axis, EcologyPersonality.Range> out = new EnumMap<>(EcologyPersonality.Axis.class);
        for (EcologyPersonality.Axis axis : EcologyPersonality.Axis.values()) {
            out.put(axis, new EcologyPersonality.Range(0.5f, 0.5f));
        }
        return out;
    }

    /** A profile with exactly these capabilities and needs (default figures), neutral temperament. */
    static EcologyProfile profile(EnumSet<EcologyCapability> capabilities, EcologyNeed... needs) {
        return profile(capabilities, 16, needs);
    }

    static EcologyProfile profile(EnumSet<EcologyCapability> capabilities, double senseRadius, EcologyNeed... needs) {
        Map<EcologyNeed, Integer> declared = new EnumMap<>(EcologyNeed.class);
        for (EcologyNeed need : needs) {
            declared.put(need, need.defaultTicks());
        }
        return new EcologyProfile(capabilities, senseRadius, 10, 16, 6000, declared, NEUTRAL, 12,
                EcologyProfile.HuntStyle.CHASE, Optional.empty(), Optional.empty());
    }

    /** Gives {@code creature} this profile; the returned undo puts the shipped one back (and runs anyway, late). */
    private static Runnable as(GameTestHelper helper, String creature, EcologyProfile profile) {
        AutoCloseable handle = EcologyProfileRegistry.override(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, creature), profile);
        return TestCleanup.guard(helper.getLevel(), UNDO_BY, () -> {
            try {
                handle.close();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Runnable undo(GameTestHelper helper, Runnable undo) {
        return TestCleanup.guard(helper.getLevel(), UNDO_BY, undo);
    }

    /** Runs undos, last first. */
    private static void undoAll(Runnable... undos) {
        for (int i = undos.length - 1; i >= 0; i--) {
            if (undos[i] != null) {
                undos[i].run();
            }
        }
    }

    /** A floor of {@code block} from x0..x1, z0..z1 under the arena, fenced with glass two high; returns its undo. */
    private static Runnable pen(GameTestHelper helper, int x0, int x1, int z0, int z1, Block block) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x < x0 || x > x1 || z < z0 || z > z1;
                helper.setBlock(new BlockPos(x, Y - 1, z), edge ? Blocks.STONE : block);
                if (edge) {
                    helper.setBlock(new BlockPos(x, Y, z), Blocks.GLASS);
                    helper.setBlock(new BlockPos(x, Y + 1, z), Blocks.GLASS);
                }
            }
        }
        return undo(helper, () -> {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    for (int y = Y - 1; y <= Y + 3; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        });
    }

    /** A creature on the arena floor, awake and under gravity. */
    private static PathfinderMob alive(CreatureArena arena, String id, int x, int z) {
        PathfinderMob mob = arena.creature(id, x, z);
        mob.setNoAi(false);
        mob.setNoGravity(false);
        return mob;
    }

    private static void chunksTick(GameTestHelper helper) {
        WizardTestSupport.checkChunksTick(helper, new BlockPos(-2, Y, -2), new BlockPos(24, Y, 4));
    }

    // ── needs, with real movement ───────────────────────────────────────────────────────────────

    private static void drinksAtWater(GameTestHelper helper) {
        Runnable[] undo = new Runnable[3];
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob[] mob = new PathfinderMob[1];
        helper.startSequence()
                .thenWaitUntil(() -> chunksTick(helper))
                .thenExecute(() -> {
                    // Not prey: an explosion in the neighbouring noise scenario must not send it running.
                    undo[0] = as(helper, DRINKER, profile(EnumSet.of(EcologyCapability.SOLITARY), EcologyNeed.THIRST));
                    undo[1] = pen(helper, 0, 15, 0, 3, Blocks.STONE);
                    for (int x = 9; x <= 12; x++) {
                        for (int z = 0; z <= 3; z++) {
                            helper.setBlock(new BlockPos(x, Y - 1, z), Blocks.WATER);
                        }
                    }
                    undo[2] = undo(helper, arena::clear);
                    mob[0] = alive(arena, DRINKER, 2, 1);
                    EcologySensing.state(mob[0]).needs().set(EcologyNeed.THIRST, 0.9f);
                })
                .thenWaitUntil(() -> {
                    EcologyState state = EcologySensing.state(mob[0]);
                    check(helper, state.needs().get(EcologyNeed.THIRST) <= 0.15f,
                            () -> "still thirsty (" + state.needs().get(EcologyNeed.THIRST) + "), doing " + state.intent()
                                    + ", water known at " + state.waterAt() + ", standing at " + mob[0].position()
                                    + ", history " + state.history() + ", perceived " + state.perception());
                })
                .thenExecute(() -> undoAll(undo))
                .thenSucceed();
    }

    private static void grazes(GameTestHelper helper) {
        Runnable[] undo = new Runnable[3];
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob[] mob = new PathfinderMob[1];
        boolean[] shown = new boolean[1];
        helper.startSequence()
                .thenWaitUntil(() -> chunksTick(helper))
                .thenExecute(() -> {
                    undo[0] = as(helper, GRAZER, profile(EnumSet.of(EcologyCapability.GRAZER), EcologyNeed.HUNGER));
                    undo[1] = pen(helper, 0, 10, 0, 3, Blocks.GRASS_BLOCK);
                    undo[2] = undo(helper, arena::clear);
                    mob[0] = alive(arena, GRAZER, 2, 1);
                    EcologySensing.state(mob[0]).needs().set(EcologyNeed.HUNGER, 0.9f);
                })
                .thenWaitUntil(() -> {
                    EcologyState state = EcologySensing.state(mob[0]);
                    // The activity the client animates from: it must say EAT at some point during the meal.
                    shown[0] |= mob[0] instanceof HasActivity a && a.activity() == CreatureActivity.EAT;
                    check(helper, state.needs().get(EcologyNeed.HUNGER) <= 0.15f,
                            () -> "still hungry (" + state.needs().get(EcologyNeed.HUNGER) + "), doing " + state.intent()
                                    + ", forage known at " + state.forageAt() + ", at " + mob[0].position()
                                    + ", history " + state.history() + ", perceived " + state.perception());
                })
                .thenExecute(() -> {
                    check(helper, shown[0], () -> "it ate without ever showing EAT");
                    undoAll(undo);
                })
                .thenSucceed();
    }

    /** Water on the far side of a wall: it tries, makes no progress, gives up, and does not try again at once. */
    private static void givesUpOnUnreachableWater(GameTestHelper helper) {
        Runnable[] undo = new Runnable[3];
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob[] mob = new PathfinderMob[1];
        helper.startSequence()
                .thenWaitUntil(() -> chunksTick(helper))
                .thenExecute(() -> {
                    undo[0] = as(helper, QUITTER, profile(EnumSet.of(EcologyCapability.PREY), EcologyNeed.THIRST));
                    undo[1] = pen(helper, 0, 4, 0, 3, Blocks.STONE); // a closed pen: nowhere to go
                    undo[2] = undo(helper, arena::clear);
                    mob[0] = alive(arena, QUITTER, 2, 1);
                    EcologyState state = EcologySensing.state(mob[0]);
                    state.needs().set(EcologyNeed.THIRST, 0.9f);
                    Vec3 far = Vec3.atBottomCenterOf(arena.absolute(14, 1));
                    state.rememberWater(far, far.add(1, -1, 0), helper.getLevel().getGameTime() + 6000);
                })
                .thenWaitUntil(() -> {
                    EcologyState state = EcologySensing.state(mob[0]);
                    check(helper, state.failures(EcologyIntent.DRINK) >= 1,
                            () -> "never gave up: doing " + state.intent() + " at " + mob[0].position());
                })
                .thenExecute(() -> {
                    EcologyState state = EcologySensing.state(mob[0]);
                    long now = helper.getLevel().getGameTime();
                    check(helper, state.cooling(EcologyIntent.DRINK, now), () -> "a failed drink is tried again at once");
                    check(helper, state.intent() != EcologyIntent.DRINK, () -> "still set on drinking after failing");
                    undoAll(undo);
                })
                .thenSucceed();
    }

    // ── rhythm, weather, hazard ─────────────────────────────────────────────────────────────────

    private static void sleepsByRhythm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long original = level.getDayTime();
        Runnable profile = as(helper, SLEEPER, profile(EnumSet.of(EcologyCapability.DIURNAL)));
        Runnable time = undo(helper, () -> level.setDayTime(original));
        CreatureArena arena = CreatureArena.at(helper, Y);
        Runnable creatures = undo(helper, arena::clear);
        PathfinderMob[] mob = new PathfinderMob[1];
        helper.startSequence()
                .thenExecute(() -> {
                    level.setDayTime(18000L);
                    mob[0] = arena.creature(SLEEPER, 1, 1);
                })
                .thenIdle(2)
                .thenExecute(() -> arena.expect(mob[0], EcologyIntent.SLEEP))
                .thenExecute(() -> level.setDayTime(6000L))
                .thenIdle(2)
                .thenExecute(() -> arena.expect(mob[0], EcologyIntent.NONE))
                .thenExecute(() -> undoAll(profile, time, creatures))
                .thenSucceed();
    }

    private static void sheltersFromRain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean wasRaining = level.isRaining();
        Runnable profile = as(helper, SHELTERER, profile(EnumSet.of(EcologyCapability.WEATHER_SHY)));
        Runnable weather = undo(helper, () -> level.setWeatherParameters(wasRaining ? 0 : 6000, wasRaining ? 6000 : 0,
                wasRaining, false));
        // High above every other scenario's pens: neighbouring scenarios sit close, and any block of theirs over this
        // column would take the open sky away before the roof goes on.
        int high = Y + 40;
        CreatureArena arena = CreatureArena.at(helper, high);
        BlockPos roof = new BlockPos(1, high + 3, 1);
        Runnable built = undo(helper, () -> {
            helper.setBlock(roof, Blocks.AIR);
            arena.clear();
        });
        PathfinderMob[] mob = new PathfinderMob[1];
        helper.startSequence()
                .thenExecute(() -> {
                    mob[0] = arena.creature(SHELTERER, 1, 1);
                    check(helper, level.getBiome(mob[0].blockPosition()).value().hasPrecipitation(),
                            () -> "setup: the test world's biome has no rain to shelter from");
                    level.setWeatherParameters(0, 6000, true, false);
                })
                // Rain fades in: isRaining() reads the rain level, which climbs a little each tick.
                .thenWaitUntil(() -> check(helper, level.isRaining(), () -> "the rain never came"))
                .thenExecute(() -> arena.expect(mob[0], EcologyIntent.SHELTER_FROM_WEATHER))
                .thenExecute(() -> helper.setBlock(roof, Blocks.STONE))
                .thenIdle(EcologyIntent.SHELTER_FROM_WEATHER.minTicks() + 5) // past its minimum: only "sustains" holds it
                // Under cover in the rain it stays put — the brain alone would say NONE; commitment keeps it there.
                .thenExecute(() -> arena.expect(mob[0], EcologyIntent.SHELTER_FROM_WEATHER))
                .thenExecute(() -> level.setWeatherParameters(6000, 0, false, false))
                .thenWaitUntil(() -> check(helper, !level.isRaining(), () -> "the rain never stopped"))
                .thenExecute(() -> arena.expect(mob[0], EcologyIntent.NONE))
                .thenExecute(() -> undoAll(profile, weather, built))
                .thenSucceed();
    }

    private static void fleesFire(GameTestHelper helper) {
        Runnable profile = as(helper, BURNT, profile(EnumSet.of(EcologyCapability.GROOMING)));
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob mob = arena.creature(BURNT, 1, 1);
            arena.expect(mob, EcologyIntent.NONE); // calm, and too new to have settled into grooming yet
            mob.igniteForSeconds(5);
            arena.expect(mob, EcologyIntent.FLEE_HAZARD);
            mob.clearFire();
            arena.expect(mob, EcologyIntent.NONE); // out of the fire: it stops running at once
            helper.succeed();
        } finally {
            arena.clear();
            profile.run();
        }
    }

    // ── kin, noise, remains ─────────────────────────────────────────────────────────────────────

    private static void solitaryKeepsDistance(GameTestHelper helper) {
        Runnable profile = as(helper, LONER, profile(EnumSet.of(EcologyCapability.SOLITARY)));
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob loner = arena.creature(LONER, 1, 1);
            arena.expect(loner, EcologyIntent.NONE);
            arena.creature(LONER, 7, 1);
            arena.expect(loner, EcologyIntent.KEEP_DISTANCE);
            helper.succeed();
        } finally {
            arena.clear();
            profile.run();
        }
    }

    private static void noiseScattersAndDraws(GameTestHelper helper) {
        Runnable timid = as(helper, STARTLED, profile(EnumSet.of(EcologyCapability.PREY)));
        Runnable curious = as(helper, NOSY, profile(EnumSet.of(EcologyCapability.CURIOUS)));
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob prey = arena.creature(STARTLED, 1, 1);
            PathfinderMob nosy = arena.creature(NOSY, 1, 3);
            BlockPos blast = arena.absolute(12, 2);
            long dropped = AiMetrics.get(AiMetrics.Metric.NOISE_DROPPED);
            long queries = AiMetrics.get(AiMetrics.Metric.NOISE_QUERY);
            helper.getLevel().gameEvent(null, GameEvent.EXPLODE, blast);
            helper.getLevel().gameEvent(null, GameEvent.EXPLODE, blast); // same chunk, same tick
            check(helper, AiMetrics.get(AiMetrics.Metric.NOISE_QUERY) - queries == 1,
                    () -> "two explosions in one chunk in one tick made "
                            + (AiMetrics.get(AiMetrics.Metric.NOISE_QUERY) - queries) + " queries, expected 1");
            check(helper, AiMetrics.get(AiMetrics.Metric.NOISE_DROPPED) > dropped, () -> "the second explosion was not dropped");
            arena.expect(prey, EcologyIntent.FLEE_STIMULUS);
            arena.expect(nosy, EcologyIntent.INVESTIGATE);
            helper.succeed();
        } finally {
            arena.clear();
            undoAll(timid, curious);
        }
    }

    /**
     * A shy creature that sees only six blocks: a person standing nine blocks off is out of sight and out of mind;
     * the same person running there is heard (a running step carries ten blocks) and sends it off.
     */
    private static void runningPersonFrightensShy(GameTestHelper helper) {
        Runnable shy = as(helper, SHY, profile(EnumSet.of(EcologyCapability.HUMAN_AVOIDANT), 6));
        CreatureArena arena = CreatureArena.at(helper, Y);
        ServerPlayer runner = WizardTestSupport.placeMockPlayer(helper, "Runner", GameType.SURVIVAL);
        try {
            PathfinderMob unicorn = arena.creature(SHY, 1, 1);
            Vec3 at = Vec3.atBottomCenterOf(arena.absolute(9, 1));
            runner.teleportTo(at.x, at.y, at.z);
            runner.setNoGravity(true);
            arena.expect(unicorn, EcologyIntent.NONE);
            helper.getLevel().gameEvent(runner, GameEvent.STEP, arena.absolute(9, 1));
            arena.expect(unicorn, EcologyIntent.NONE); // walking: a step is no noise
            runner.setSprinting(true);
            helper.getLevel().gameEvent(runner, GameEvent.STEP, arena.absolute(9, 1));
            arena.expect(unicorn, EcologyIntent.FLEE_STIMULUS);
            helper.succeed();
        } finally {
            retire(helper, runner);
            arena.clear();
            shy.run();
        }
    }

    private static void scavengerVisitsKill(GameTestHelper helper) {
        // Short senses (4 blocks), so food a neighbouring scenario dropped is not in view: food on the ground rightly
        // comes before remains, and this is about the remains.
        Runnable profile = as(helper, SCAVENGER, profile(EnumSet.of(EcologyCapability.SCAVENGER), 4));
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            // No "nothing yet" check: the death log is per level, and a neighbouring scenario's kill may be in smelling
            // range already. What matters is that it goes for the nearest remains.
            PathfinderMob scavenger = arena.creature(SCAVENGER, 1, 1);
            Chicken chicken = arena.spawn(EntityType.CHICKEN, 4, 1);
            chicken.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 100f);
            check(helper, !chicken.isAlive(), () -> "setup: the chicken survived");
            // The drops would rightly come first (food on the ground); this is about the remains.
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, chicken.getBoundingBox().inflate(4))
                    .forEach(Entity::discard);
            Vec3 died = chicken.position();
            arena.expect(scavenger, EcologyIntent.SCAVENGE);
            // Neighbouring scenarios kill things too, sometimes nearer than this chicken: the rule is "the nearest
            // remains", so it must be going to this kill or to something no farther off.
            Vec3 smelled = EcologySensing.state(scavenger).carrionAt();
            Vec3 here = scavenger.position();
            check(helper, smelled != null && smelled.distanceTo(here) <= died.distanceTo(here) + 0.5,
                    () -> "the scavenger is going to " + smelled + ", farther than the kill at " + died);
            helper.succeed();
        } finally {
            arena.clear();
            profile.run();
        }
    }

    // ── persistence and cost ────────────────────────────────────────────────────────────────────

    private static void stateSurvivesSave(GameTestHelper helper) {
        Runnable profile = as(helper, SAVED, profile(EnumSet.of(EcologyCapability.GRAZER, EcologyCapability.HERD),
                EcologyNeed.HUNGER, EcologyNeed.SOCIAL));
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob mob = arena.creature(SAVED, 1, 1);
            PathfinderMob untouched = arena.creature(SAVED, 5, 1);
            arena.think(mob);
            EcologyState state = EcologySensing.state(mob);
            state.needs().set(EcologyNeed.HUNGER, 0.6f);
            state.needs().set(EcologyNeed.SOCIAL, 0.3f);
            Vec3 home = state.home();
            PathfinderMob copy = CreatureWildlifeTests.roundTrip(helper, mob);
            EcologyState loaded = EcologySensing.state(copy);
            check(helper, home != null && home.equals(loaded.home()), () -> "home " + home + " came back as " + loaded.home());
            check(helper, Math.abs(loaded.needs().get(EcologyNeed.HUNGER) - 0.6f) < 1e-4,
                    () -> "hunger came back as " + loaded.needs().get(EcologyNeed.HUNGER));
            check(helper, Math.abs(loaded.needs().get(EcologyNeed.SOCIAL) - 0.3f) < 1e-4,
                    () -> "loneliness came back as " + loaded.needs().get(EcologyNeed.SOCIAL));
            check(helper, loaded.personality() == null, () -> "personality is derived, never saved");
            EcologyState fresh = EcologySensing.state(CreatureWildlifeTests.roundTrip(helper, untouched));
            check(helper, fresh.home() == null && !fresh.worthSaving(), () -> "a creature that never looked saved a home");
            helper.succeed();
        } finally {
            arena.clear();
            profile.run();
        }
    }

    /**
     * The performance contract with needs, rhythm and resource searches switched on: forty creatures in a pen with
     * grass and water, every need pressing, for 200 ticks. Looks stay on the MEDIUM beat, resource searches on the
     * SLOW beat, and nothing runs per tick.
     */
    private static void fortyWithinBudget(GameTestHelper helper) {
        int count = 40;
        int ticks = 200; // the scenario has an 800-tick limit: room for the chunk wait on a loaded test server
        Runnable[] undo = new Runnable[3];
        CreatureArena arena = CreatureArena.at(helper, Y);
        List<PathfinderMob> herd = new ArrayList<>();
        long[] before = new long[4];
        long[] nanos = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> chunksTick(helper))
                .thenExecute(() -> {
                    undo[0] = as(helper, HERD, profile(EnumSet.of(EcologyCapability.PREY, EcologyCapability.HERD,
                                    EcologyCapability.GRAZER, EcologyCapability.GROOMING, EcologyCapability.WEATHER_SHY),
                            EcologyNeed.HUNGER, EcologyNeed.THIRST, EcologyNeed.ENERGY, EcologyNeed.SOCIAL, EcologyNeed.SAFETY));
                    undo[1] = pen(helper, 0, 22, 0, 3, Blocks.GRASS_BLOCK);
                    undo[2] = undo(helper, arena::clear);
                    for (int z = 0; z <= 3; z++) {
                        for (int x = 18; x <= 21; x++) {
                            helper.setBlock(new BlockPos(x, Y - 1, z), Blocks.WATER);
                        }
                    }
                    for (int i = 0; i < count; i++) {
                        PathfinderMob mob = alive(arena, HERD, 1 + (i % 16), i / 16);
                        EcologyState state = EcologySensing.state(mob);
                        state.needs().set(EcologyNeed.HUNGER, 0.5f + (i % 5) * 0.1f);
                        state.needs().set(EcologyNeed.THIRST, 0.9f - (i % 5) * 0.1f);
                        herd.add(mob);
                    }
                    before[0] = AiMetrics.get(AiMetrics.Metric.ECOLOGY_LOOK);
                    before[1] = AiMetrics.get(AiMetrics.Metric.RESOURCE_SEARCH);
                    before[2] = AiMetrics.get(AiMetrics.Metric.PATH_REQUEST);
                    before[3] = AiMetrics.get(AiMetrics.Metric.EVENT_WAKE);
                    nanos[0] = System.nanoTime();
                })
                .thenIdle(ticks)
                .thenExecute(() -> {
                    double ms = (System.nanoTime() - nanos[0]) / 1e6 / ticks;
                    long looks = AiMetrics.get(AiMetrics.Metric.ECOLOGY_LOOK) - before[0];
                    long searches = AiMetrics.get(AiMetrics.Metric.RESOURCE_SEARCH) - before[1];
                    long wakes = AiMetrics.get(AiMetrics.Metric.EVENT_WAKE) - before[3];
                    long scheduled = (long) count * ticks / EcologySensing.INTERVAL;
                    // Scheduled looks plus one per event wake (a finished meal or drink wakes it to choose again).
                    check(helper, looks <= scheduled + wakes + count,
                            () -> looks + " looks in " + ticks + " ticks for " + count + " creatures; schedule allows "
                                    + scheduled + " + " + wakes + " event wakes");
                    long allowed = (long) count * 2L * (ticks / 100 + 1);
                    check(helper, searches <= allowed,
                            () -> searches + " resource searches; the SLOW tier allows " + allowed);
                    long busy = herd.stream().filter(m -> EcologySensing.state(m).intent() != EcologyIntent.NONE
                            || EcologySensing.state(m).needs().get(EcologyNeed.THIRST) < 0.5f).count();
                    check(helper, busy >= count / 4, () -> "only " + busy + " of " + count + " creatures did anything about their needs");
                    helper.getLevel().getServer().sendSystemMessage(Component.literal(
                            "[ecology budget] " + count + " creatures, " + ticks + " ticks: " + String.format("%.2f", ms)
                                    + " ms/tick wall (whole server), looks=" + looks + " searches=" + searches
                                    + " wakes=" + wakes + " paths=" + (AiMetrics.get(AiMetrics.Metric.PATH_REQUEST) - before[2])));
                    undoAll(undo);
                })
                .thenSucceed();
    }
}
