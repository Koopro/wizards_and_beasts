package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.creature.ecology.CreatureStimuli;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent;
import at.koopro.wizardsandbeasts.creature.ecology.EcologySensing;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The ecology layer against the real world, one behaviour per scenario, through {@link CreatureArena}. Every
 * scenario runs alone: creatures sense up to 32 blocks, and a neighbouring scenario's acromantula is a predator.
 */
public final class EcologyTests {

    private static final int Y = 4;

    private EcologyTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("ecology_prey_flees_a_predator", "ecology: a unicorn runs from an acromantula",
                EcologyTests::preyFleesPredator);
        tests.addAlone("ecology_herd_regroups", "ecology: a unicorn that strays from its herd goes back",
                EcologyTests::herdRegroups);
        tests.addAlone("ecology_territory_is_warned_then_defended",
                "ecology: a bowtruckle warns a stranger, then defends; it lets its own person near",
                EcologyTests::territoryWarnedThenDefended);
        tests.addAlone("ecology_predator_hunts_until_fed", "ecology: a hippogriff hunts a chicken, eats, and stops",
                EcologyTests::predatorHuntsUntilFed);
        tests.addAlone("ecology_magic_draws_the_curious_and_scatters_prey",
                "ecology: a spell brings a zouwu to look and sends a unicorn running",
                EcologyTests::magicDrawsCuriousScattersPrey);
        tests.addAlone("ecology_herd_scatters_when_one_falls", "ecology: unicorns bolt when one of them dies",
                EcologyTests::herdScattersOnDeath);
        tests.addAlone("ecology_nocturnal_rests_by_day", "ecology: an acromantula rests by day, and at night hunts a unicorn",
                EcologyTests::nocturnalRestsByDay);
        tests.addAlone("ecology_watching_writes_a_field_note",
                "ecology: a player who sees a herd regroup has it in their bestiary",
                EcologyTests::watchingWritesFieldNote);
        tests.addAlone("ecology_looks_around_only_every_second",
                "ecology: a creature with ecology looks around once per interval, not every tick",
                EcologyTests::looksOnlyOnInterval);
    }

    private static void preyFleesPredator(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob unicorn = arena.creature("unicorn", 1, 1);
            arena.expect(unicorn, EcologyIntent.NONE); // nothing yet
            arena.creature("acromantula", 8, 1);
            arena.expect(unicorn, EcologyIntent.FLEE_PREDATOR);
            helper.succeed();
        } finally {
            arena.clear();
        }
    }

    private static void herdRegroups(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            arena.creature("unicorn", 1, 1);
            arena.creature("unicorn", 2, 1);
            PathfinderMob stray = arena.creature("unicorn", 20, 1);
            arena.expect(stray, EcologyIntent.REGROUP);
            PathfinderMob close = arena.creature("unicorn", 3, 1);
            // Near the others the centre moves with the stray counted elsewhere; still, the close one is home.
            check(helper, arena.think(close).intent() != EcologyIntent.REGROUP, () -> "a unicorn beside its herd regrouped");
            helper.succeed();
        } finally {
            arena.clear();
        }
    }

    private static void territoryWarnedThenDefended(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        ServerPlayer stranger = WizardTestSupport.placeMockPlayer(helper, "TreeVisitor", GameType.SURVIVAL);
        try {
            PathfinderMob bowtruckle = arena.creature("bowtruckle", 1, 1);
            Vec3 at = Vec3.atBottomCenterOf(arena.absolute(7, 1));
            stranger.teleportTo(at.x, at.y, at.z);
            stranger.setNoGravity(true);
            arena.expect(bowtruckle, EcologyIntent.WARN_INTRUDER); // six blocks: inside 8, outside half
            Vec3 near = Vec3.atBottomCenterOf(arena.absolute(3, 1));
            stranger.teleportTo(near.x, near.y, near.z);
            arena.expect(bowtruckle, EcologyIntent.DEFEND_TERRITORY);
            helper.succeed();
        } finally {
            retire(helper, stranger);
            arena.clear();
        }
    }

    private static void predatorHuntsUntilFed(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob hippogriff = arena.creature("hippogriff", 1, 1);
            Chicken first = arena.spawn(EntityType.CHICKEN, 6, 1);
            arena.expect(hippogriff, EcologyIntent.HUNT);
            // Any kill of its prey feeds it — its own hunt, or its melee goal.
            first.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(hippogriff), 100f);
            check(helper, !first.isAlive(), () -> "setup: the chicken survived");
            arena.spawn(EntityType.CHICKEN, 7, 1);
            arena.expect(hippogriff, EcologyIntent.NONE);
            check(helper, EcologySensing.state(hippogriff).fedAt(helper.getLevel().getGameTime()),
                    () -> "a hippogriff that killed a chicken is not fed");
            helper.succeed();
        } finally {
            arena.clear();
        }
    }

    private static void magicDrawsCuriousScattersPrey(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob zouwu = arena.creature("zouwu", 1, 1);
            PathfinderMob unicorn = arena.creature("unicorn", 1, 3);
            CreatureStimuli.spellCast(helper.getLevel(), Vec3.atBottomCenterOf(arena.absolute(10, 2)));
            arena.expect(zouwu, EcologyIntent.INVESTIGATE);
            arena.expect(unicorn, EcologyIntent.FLEE_STIMULUS);
            helper.succeed();
        } finally {
            arena.clear();
        }
    }

    private static void herdScattersOnDeath(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            PathfinderMob survivor = arena.creature("unicorn", 1, 1);
            PathfinderMob fallen = arena.creature("unicorn", 4, 1);
            arena.expect(survivor, EcologyIntent.NONE);
            fallen.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 1000f);
            check(helper, !fallen.isAlive(), () -> "setup: the unicorn survived");
            arena.expect(survivor, EcologyIntent.FLEE_STIMULUS);
            helper.succeed();
        } finally {
            arena.clear();
        }
    }

    private static void nocturnalRestsByDay(GameTestHelper helper) {
        long original = helper.getLevel().getDayTime();
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob[] spider = new PathfinderMob[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.getLevel().setDayTime(6000L); // noon
                    spider[0] = arena.creature("acromantula", 1, 1);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    EcologyIntent day = arena.think(spider[0]).intent();
                    check(helper, day == EcologyIntent.REST || day == EcologyIntent.SEEK_SHELTER,
                            () -> "an acromantula at noon decided " + day);
                    helper.getLevel().setDayTime(18000L); // midnight
                })
                .thenIdle(2)
                .thenExecute(() -> arena.expect(spider[0], EcologyIntent.NONE))
                // Awake, a hungry acromantula hunts what the Forbidden Forest has: a unicorn.
                .thenExecute(() -> {
                    arena.creature("unicorn", 9, 1);
                    arena.expect(spider[0], EcologyIntent.HUNT);
                })
                .thenExecute(() -> {
                    helper.getLevel().setDayTime(original);
                    arena.clear();
                })
                .thenSucceed();
    }

    private static void watchingWritesFieldNote(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        ServerPlayer naturalist = WizardTestSupport.placeMockPlayer(helper, "Naturalist", GameType.SURVIVAL);
        try {
            arena.creature("unicorn", 1, 1);
            arena.creature("unicorn", 2, 1);
            PathfinderMob stray = arena.creature("unicorn", 20, 1);
            // Watching from the side, on the arena floor, beyond the herd's avoidance distance of everyone.
            Vec3 at = Vec3.atBottomCenterOf(arena.absolute(20, -12));
            naturalist.teleportTo(at.x, at.y, at.z);
            naturalist.setNoGravity(true);
            arena.expect(stray, EcologyIntent.REGROUP);
            BestiaryEntry entry = BestiaryDiscoveryHandler.entriesFor(stray.getType()).getFirst();
            boolean noted = naturalist.getData(ModAttachments.BESTIARY_DATA.get()).notesOn(entry.id()).contains("herd");
            check(helper, noted, () -> "a player watching a unicorn regroup has no 'herd' field note: "
                    + naturalist.getData(ModAttachments.BESTIARY_DATA.get()).fieldNotes());
            helper.succeed();
        } finally {
            retire(helper, naturalist);
            arena.clear();
        }
    }

    /** The performance contract: one look per {@link EcologySensing#INTERVAL} ticks, never one per tick. */
    private static void looksOnlyOnInterval(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob[] unicorn = new PathfinderMob[1];
        long[] before = new long[1];
        helper.startSequence()
                .thenExecute(() -> {
                    unicorn[0] = arena.creature("unicorn", 1, 1);
                    unicorn[0].setNoAi(false);
                    before[0] = EcologySensing.looksTaken();
                })
                .thenIdle(EcologySensing.INTERVAL * 3)
                .thenExecute(() -> {
                    long looks = EcologySensing.looksTaken() - before[0];
                    check(helper, looks >= 2 && looks <= 4,
                            () -> "one unicorn looked around " + looks + " times in " + EcologySensing.INTERVAL * 3
                                    + " ticks; expected about 3");
                    arena.clear();
                })
                .thenSucceed();
    }

    static String modid() {
        return WizardsAndBeastsMod.MODID;
    }
}
