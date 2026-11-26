package at.koopro.wizardsandbeasts.wand.making;

import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Act;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Beat;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Hands;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Odds;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Stage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchCraftTest {

    private static final int ALL_TOOLS = 31;
    private static final float ASH = 3.0f;
    private static final float ELDER = 6.7f;
    private static final float HOLLY = 2.0f;
    private static final CoreMaking UNICORN = new CoreMaking(0f, 0f, Map.of(), new CoreMaking.Pulse(18, 0f, 0f));
    private static final CoreMaking DRAGON = new CoreMaking(1.3f, 1f, Map.of(), new CoreMaking.Pulse(13, 0.65f, 0.1f));
    private static final CoreMaking PHOENIX = new CoreMaking(0.5f, 0f, Map.of(), new CoreMaking.Pulse(10, 0.2f, 0.7f));

    private static Odds odds(Act act, Hands hands, int tools, float wood) {
        return BenchCraft.odds(act, hands, tools, wood, DRAGON, 0);
    }

    @Test
    void aMakingRunsSeatedTurnedBoredMarriedThenSealsShut() {
        assertEquals(Act.TURN, Act.next(Stage.NONE), "a bare bench seats its blank and turns it in one go");
        assertEquals(Act.TURN, Act.next(Stage.SEATED));
        assertEquals(Act.BORE, Act.next(Stage.TURNED));
        assertEquals(Act.MARRY, Act.next(Stage.BORED));
        assertEquals(Act.SEAL, Act.next(Stage.MARRIED));
        assertEquals(Stage.TURNED, Act.TURN.to());
        assertEquals(Stage.MARRIED, Act.MARRY.to());
        assertNull(Act.SEAL.to(), "sealing ends the making in first sparks");
        assertEquals(BenchCraft.Risk.CRACK, Act.TURN.risk());
        assertEquals(BenchCraft.Risk.CRACK, Act.BORE.risk());
        assertEquals(BenchCraft.Risk.CORE_LOST, Act.MARRY.risk());
        assertEquals(BenchCraft.Risk.NONE, Act.SEAL.risk());
    }

    @Test
    void theVocationIsMostOfTheSkillAndOnlyAWandmakerIsForgiven() {
        assertEquals(0f, Hands.NOVICE.skill());
        assertEquals(0, Hands.NOVICE.forgive());
        assertEquals(1f, Hands.MASTER.skill(), 1e-6);
        assertEquals(2, Hands.MASTER.forgive());
        assertEquals(1, new Hands(true, false, 0).forgive());
        assertEquals(0, new Hands(false, true, 7).forgive(), "career and lore without the vocation forgive nothing");
        assertTrue(new Hands(true, false, 0).skill() > new Hands(false, true, 4).skill());
    }

    @Test
    void toolsSkillAndAnEasyWoodWidenTheWindowAndLowerTheRisk() {
        Odds bare = odds(Act.TURN, Hands.NOVICE, 0, ASH);
        Odds chisel = odds(Act.TURN, Hands.NOVICE, BenchTool.CHISEL.bit(), ASH);
        Odds master = odds(Act.TURN, Hands.MASTER, ALL_TOOLS, ASH);
        assertTrue(chisel.ease() > bare.ease() && chisel.risk() < bare.risk(), "the act's own tool helps");
        assertTrue(odds(Act.TURN, Hands.NOVICE, BenchTool.CHISEL.bit() | BenchTool.KNIFE.bit(), ASH).ease() > chisel.ease(),
                "the knife pares the blank first");
        assertEquals(bare.ease(), odds(Act.TURN, Hands.NOVICE, BenchTool.TONGS.bit(), ASH).ease(), "another act's tool does nothing");
        assertTrue(master.window() > bare.window() && master.risk() < bare.risk());
        assertTrue(odds(Act.BORE, Hands.MASTER, ALL_TOOLS, ELDER).risk() > odds(Act.BORE, Hands.MASTER, ALL_TOOLS, HOLLY).risk(),
                "elder is harder to bore than holly");
        assertTrue(bare.window() >= 1, "a window is never shut");
    }

    @Test
    void theRiskIsBoundedAndSealingRisksNothing() {
        for (Act act : Act.values()) {
            for (Hands h : new Hands[]{Hands.NOVICE, Hands.MASTER}) {
                for (float wood : new float[]{HOLLY, ELDER, 12f}) {
                    Odds o = odds(act, h, 0, wood);
                    if (act == Act.SEAL) {
                        assertEquals(0f, o.risk());
                    } else {
                        assertTrue(o.risk() >= 0.02f && o.risk() <= 0.40f, act + " risk " + o.risk());
                    }
                }
            }
        }
        Odds turn = odds(Act.TURN, Hands.NOVICE, 0, ELDER);
        assertEquals(turn.risk(), BenchCraft.chance(turn, 0f), 1e-6);
        assertEquals(0f, BenchCraft.chance(turn, 1f), 1e-6, "a perfect act risks nothing");
    }

    @Test
    void complementaryWoodsCalmTheCore() {
        Odds neutral = BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, DRAGON, 0);
        Odds suited = BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, DRAGON, 1);
        assertTrue(suited.pulse().jitter() < neutral.pulse().jitter());
        assertTrue(suited.hardness() < neutral.hardness() && suited.risk() < neutral.risk());
        Odds unicorn = BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, UNICORN, 0);
        assertTrue(unicorn.risk() < neutral.risk(), "unicorn is the most consistent core");
    }

    @Test
    void theBeatIsTheSameOnBothSidesForOneSeed() {
        Odds o = BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, PHOENIX, 0);
        assertEquals(BenchCraft.beat(o, 42L), BenchCraft.beat(o, 42L));
        assertTrue(!BenchCraft.beat(o, 42L).equals(BenchCraft.beat(o, 43L)), "another seed, another beat");
        boolean[] held = new boolean[BenchCraft.BORE_TICKS];
        Odds bore = odds(Act.BORE, Hands.NOVICE, 0, ASH);
        assertEquals(BenchCraft.scoreBore(bore, 7L, held), BenchCraft.scoreBore(bore, 7L, held));
    }

    @Test
    void beatsComeInOrderNeverTwoAtOnceAndTheActOutlastsTheLast() {
        for (CoreMaking core : new CoreMaking[]{UNICORN, DRAGON, PHOENIX}) {
            for (long seed = 0; seed < 50; seed++) {
                Beat beat = BenchCraft.beat(BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, core, 0), seed);
                int[] b = beat.beats();
                assertEquals(BenchCraft.MARRY_PULSES, b.length);
                assertEquals(BenchCraft.LEAD, b[0]);
                for (int i = 1; i < b.length; i++) {
                    assertTrue(b[i] - b[i - 1] >= 6, "beats too close: " + beat);
                }
                assertTrue(beat.duration() > b[b.length - 1]);
            }
        }
        assertEquals(BenchCraft.TURN_STROKES, BenchCraft.beat(odds(Act.TURN, Hands.NOVICE, 0, ASH), 1L).beats().length);
        assertEquals(BenchCraft.SEAL_STROKES, BenchCraft.beat(odds(Act.SEAL, Hands.NOVICE, 0, ASH), 1L).beats().length);
        assertEquals(BenchCraft.LEAD + BenchCraft.BORE_TICKS, BenchCraft.beat(odds(Act.BORE, Hands.NOVICE, 0, ASH), 1L).duration());
    }

    @Test
    void unicornBeatsSteadyDragonFlaresAndPhoenixShifts() {
        int[] unicorn = BenchCraft.beat(BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, UNICORN, 0), 5L).beats();
        for (int i = 2; i < unicorn.length; i++) {
            assertEquals(unicorn[1] - unicorn[0], unicorn[i] - unicorn[i - 1], "a unicorn core never wavers");
        }
        assertTrue(spread(DRAGON) > spread(UNICORN));
        assertTrue(spread(PHOENIX) > spread(UNICORN));
    }

    /** The widest gap minus the narrowest over many seeds: how uneven a core's pulse plays. */
    private static int spread(CoreMaking core) {
        int worst = 0;
        for (long seed = 0; seed < 40; seed++) {
            int[] b = BenchCraft.beat(BenchCraft.odds(Act.MARRY, Hands.NOVICE, 0, ASH, core, 0), seed).beats();
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int i = 1; i < b.length; i++) {
                min = Math.min(min, b[i] - b[i - 1]);
                max = Math.max(max, b[i] - b[i - 1]);
            }
            worst = Math.max(worst, max - min);
        }
        return worst;
    }

    @Test
    void tapsOnTheBeatScoreFullMissesAndHammeringDoNot() {
        Odds o = odds(Act.TURN, Hands.NOVICE, 0, ASH);
        Beat beat = BenchCraft.beat(o, 9L);
        assertEquals(1f, BenchCraft.scoreTaps(beat, o, beat.beats()));
        int[] late = beat.beats().clone();
        for (int i = 0; i < late.length; i++) {
            late[i] += o.window();
        }
        assertEquals(1f, BenchCraft.scoreTaps(beat, o, late), "the window's edge still hits");
        for (int i = 0; i < late.length; i++) {
            late[i] += 1;
        }
        assertEquals(0f, BenchCraft.scoreTaps(beat, o, late), "a tick past it misses");
        assertEquals(0f, BenchCraft.scoreTaps(beat, o, new int[0]));
        int[] hammer = new int[beat.duration()];
        for (int i = 0; i < hammer.length; i++) {
            hammer[i] = i;
        }
        assertTrue(BenchCraft.scoreTaps(beat, o, hammer) < 0.2f, "pressing every tick is no way through");
    }

    @Test
    void aWandmakerIsForgivenAMiss() {
        Odds novice = odds(Act.TURN, Hands.NOVICE, 0, ASH);
        Odds maker = odds(Act.TURN, new Hands(true, false, 0), 0, ASH);
        int[] beats = BenchCraft.beat(novice, 3L).beats();
        int[] oneMissed = java.util.Arrays.copyOf(beats, beats.length - 1);
        assertTrue(BenchCraft.scoreTaps(BenchCraft.beat(novice, 3L), novice, oneMissed) < 1f);
        assertEquals(1f, BenchCraft.scoreTaps(BenchCraft.beat(maker, 3L), maker, oneMissed));
    }

    @Test
    void holdingTheBitInTheBandScoresAndLettingItSinkDoesNot() {
        Odds o = odds(Act.BORE, Hands.MASTER, ALL_TOOLS, ASH);
        long seed = 11L;
        assertTrue(BenchCraft.scoreBore(o, seed, new boolean[BenchCraft.BORE_TICKS]) < 0.5f, "a bit left to sink drifts out");
        // A steady hand: hold while the bit is below the band's middle.
        boolean[] held = new boolean[BenchCraft.BORE_TICKS];
        for (int i = 0; i < held.length; i++) {
            BenchCraft.BoreFrame[] frames = BenchCraft.bore(o, seed, held);
            float marker = i == 0 ? 0.5f : frames[i - 1].marker();
            held[i] = marker < frames[i].center();
        }
        assertTrue(BenchCraft.scoreBore(o, seed, held) > 0.7f, "a steady hand keeps the bit in the grain");
    }

    @Test
    void aCleanMakingIsWholeAndAPoorOneIsFlawed() {
        assertEquals(1f, BenchCraft.integrity(1f));
        assertEquals(1f, BenchCraft.integrity(BenchCraft.CLEAN));
        assertEquals(BenchCraft.WORST_INTEGRITY, BenchCraft.integrity(0f));
        assertTrue(BenchCraft.integrity(0.4f) > BenchCraft.integrity(0.2f));
        assertTrue(BenchCraft.integrity(0.79f) < 1f);
    }

    @Test
    void theBenchRigHasOneToolPerAct() {
        for (Act act : Act.values()) {
            assertTrue(act.tool() != null);
        }
        assertArrayEquals(new BenchTool[]{BenchTool.CHISEL, BenchTool.BRACE, BenchTool.TONGS, BenchTool.VARNISH},
                java.util.Arrays.stream(Act.values()).map(Act::tool).toArray(BenchTool[]::new));
    }
}
