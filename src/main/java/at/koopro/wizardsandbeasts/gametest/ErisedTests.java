package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.artefact.erised.ClueCandidate;
import at.koopro.wizardsandbeasts.artefact.erised.ErisedClues;
import at.koopro.wizardsandbeasts.artefact.erised.ErisedHistory;
import at.koopro.wizardsandbeasts.artefact.erised.ErisedService;
import at.koopro.wizardsandbeasts.artefact.erised.ErisedVision;
import at.koopro.wizardsandbeasts.artefact.erised.ErisedVisions;
import at.koopro.wizardsandbeasts.artefact.erised.Gazer;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.loot.SpellSites;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningEligibility;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Mirror of Erised (Phase 3b): each gazer sees their own heart's desire, the Mirror pulls at whoever lingers and
 * then shows only a reflection, and every clue it could give points at something real.
 */
public final class ErisedTests {

    /** Inside the scenario's own column (lesson 2026-10-22: scenes stay within x/z 0..4). */
    private static final BlockPos MIRROR = new BlockPos(2, 1, 2);

    private ErisedTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("artefact_erised_each_sees_their_own",
                "artefact: two gazers of different heritage are offered their own visions, and one's gazes leave "
                        + "the other's record untouched",
                ErisedTests::eachSeesTheirOwn);
        tests.add("artefact_erised_pulls_then_reflects",
                "artefact: the Mirror's first gaze is free, the next two pull harder, the fourth shows only a "
                        + "reflection, and a new day starts over",
                ErisedTests::pullsThenReflects);
        tests.add("artefact_erised_clues_point_at_real_things",
                "artefact: every clue the Mirror could give names something the gazer has not found and can reach; "
                        + "one clue comes with the day's first gaze and none after",
                ErisedTests::cluesPointAtRealThings);
    }

    private static void eachSeesTheirOwn(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer giant = player(helper, "ErisedGiant");
        ServerPlayer centaur = player(helper, "ErisedCentaur");
        try {
            placeMirror(helper);
            HeritageAPI.commit(giant, Heritage.GIANT, HeritageVariant.GIANT_FULL);
            HeritageAPI.commit(centaur, Heritage.CENTAUR, HeritageVariant.CENTAUR_STARGAZER);
            Set<String> giantSees = eligible(ErisedService.gazer(giant));
            Set<String> centaurSees = eligible(ErisedService.gazer(centaur));
            check(helper, giantSees.contains("giant_clans_at_peace") && !giantSees.contains("centaur_sky_read"),
                    () -> "the giant is offered " + giantSees);
            check(helper, centaurSees.contains("centaur_sky_read") && !centaurSees.contains("giant_clans_at_peace"),
                    () -> "the centaur is offered " + centaurSees);

            BlockPos at = helper.absolutePos(MIRROR);
            check(helper, ErisedService.gaze(giant, at) == ErisedService.Result.VISION, () -> "the giant saw nothing");
            ErisedHistory giantRecord = history(giant);
            check(helper, giantRecord.recent().size() == 1 && giantSees.contains(
                            Identifier.parse(giantRecord.recent().get(0)).getPath()),
                    () -> "the giant was shown a vision not theirs: " + giantRecord);
            check(helper, history(centaur).equals(ErisedHistory.EMPTY), () -> "the centaur's record moved");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, giant);
            WizardTestSupport.retire(helper, centaur);
            artefacts.run();
        }
    }

    private static void pullsThenReflects(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer gazer = player(helper, "ErisedLingerer");
        try {
            placeMirror(helper);
            BlockPos at = helper.absolutePos(MIRROR);
            check(helper, ErisedService.gaze(gazer, at) == ErisedService.Result.VISION, () -> "first gaze");
            check(helper, !gazer.hasEffect(MobEffects.SLOWNESS), () -> "the first gaze pulled");
            check(helper, ErisedService.gaze(gazer, at) == ErisedService.Result.VISION, () -> "second gaze");
            check(helper, amplifier(gazer) == 0 && gazer.hasEffect(MobEffects.HUNGER), () -> "the second gaze did not pull");
            check(helper, ErisedService.gaze(gazer, at) == ErisedService.Result.VISION, () -> "third gaze");
            check(helper, amplifier(gazer) == 1, () -> "the third gaze did not pull harder");
            check(helper, ErisedService.gaze(gazer, at) == ErisedService.Result.REFLECTION, () -> "a fourth vision");
            check(helper, history(gazer).gazes() == 3, () -> "the reflection was counted as a gaze");

            long today = SpellPractice.day(helper.getLevel().getServer().overworld().getGameTime());
            ErisedHistory yesterday = new ErisedHistory(today - 1, 3, history(gazer).recent(), history(gazer).clues());
            gazer.setData(ModAttachments.ERISED_HISTORY.get(), yesterday);
            check(helper, ErisedService.gaze(gazer, at) == ErisedService.Result.VISION, () -> "a new day stayed dark");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, gazer);
            artefacts.run();
        }
    }

    private static void cluesPointAtRealThings(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        Runnable spells = WizardTestSupport.leaseModule(Module.WANDS_AND_SPELLS);
        ServerPlayer gazer = player(helper, "ErisedSeeker");
        try {
            placeMirror(helper);
            WizardTestSupport.makeWandkind(gazer);
            List<ClueCandidate> candidates = ErisedClues.candidates(gazer);
            check(helper, candidates.stream().anyMatch(c -> c.kind() == ClueCandidate.Kind.SPELL),
                    () -> "a gazer who knows no spell was pointed at none: " + candidates.size() + " candidates; "
                            + "sites(stupefy)=" + SpellSites.placesOf("stupefy") + " knows="
                            + gazer.getData(ModAttachments.SPELL_DATA.get()).getKnownSpells() + " eligible(stupefy)="
                            + SpellLearningEligibility.evaluate(gazer, Spells.byId("stupefy")));
            Set<String> keys = new HashSet<>();
            for (ClueCandidate c : candidates) {
                check(helper, keys.add(c.key()), () -> "candidate twice: " + c.key());
                check(helper, reachable(gazer, c), () -> "a clue to nowhere: " + c.key());
            }

            BlockPos at = helper.absolutePos(MIRROR);
            ErisedService.gaze(gazer, at);
            List<String> afterFirst = history(gazer).clues();
            check(helper, afterFirst.size() == 1, () -> "the first gaze gave " + afterFirst.size() + " clues");
            check(helper, keys.contains(afterFirst.get(0)), () -> "the clue given was not a candidate: " + afterFirst);
            ErisedService.gaze(gazer, at);
            check(helper, history(gazer).clues().size() == 1, () -> "a second gaze in a day gave another clue");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, gazer);
            spells.run();
            artefacts.run();
        }
    }

    // ── checks ──────────────────────────────────────────────────────────

    /** The candidate's own reachability, re-derived independently of how {@link ErisedClues} built it. */
    private static boolean reachable(ServerPlayer gazer, ClueCandidate c) {
        return switch (c.kind()) {
            case SPELL -> {
                var spell = Spells.byId(c.target());
                yield spell != null && !gazer.getData(ModAttachments.SPELL_DATA.get()).knowsSpell(c.target())
                        && !SpellSites.placesOf(c.target()).isEmpty()
                        && SpellLearningEligibility.evaluate(gazer, spell).learnable();
            }
            case PLACE -> Locations.open(Identifier.parse(c.target())) != null;
            case CREATURE -> BestiaryEntryRegistry.get(Identifier.parse(c.target())) != null;
            case FLOO -> !c.target().isBlank();
        };
    }

    private static Set<String> eligible(Gazer gazer) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<Identifier, ErisedVision> e : ErisedVisions.all().entrySet()) {
            if (e.getValue().shownTo(gazer)) {
                out.add(e.getKey().getPath());
            }
        }
        return out;
    }

    private static int amplifier(ServerPlayer player) {
        MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
        return slow == null ? -1 : slow.getAmplifier();
    }

    private static ErisedHistory history(ServerPlayer player) {
        return player.getData(ModAttachments.ERISED_HISTORY.get());
    }

    private static void placeMirror(GameTestHelper helper) {
        BlockState state = ModBlocks.MIRROR_OF_ERISED.get().defaultBlockState()
                .setValue(at.koopro.wizardsandbeasts.artefact.TwoTallBlock.FACING, Direction.NORTH);
        helper.setBlock(MIRROR, state.setValue(at.koopro.wizardsandbeasts.artefact.TwoTallBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(MIRROR.above(), state.setValue(at.koopro.wizardsandbeasts.artefact.TwoTallBlock.HALF,
                DoubleBlockHalf.UPPER));
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
