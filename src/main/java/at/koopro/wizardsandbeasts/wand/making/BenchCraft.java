package at.koopro.wizardsandbeasts.wand.making;

import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Making a wand at the bench, as rules: the stages, the act that moves a blank from one to the next, the timing beat
 * each act is played as, how a performance is scored, and what it risks. Pure and deterministic, so the client and
 * the server build the same beat from the same seed, and every rule is unit-tested.
 *
 * <p>Owner answers (2026-11-26): quality is skill, tools and affinity together; making is risky (a wand can come out
 * flawed, the blank can crack, the core can be lost); each stage is a minigame; anyone may make a wand, but it is hard
 * without the Wandmaker vocation.
 *
 * <p>Canon: Ollivander's best wands are cores "expertly enclosed in specially selected and complementary wandwoods",
 * and wands before him were "of crude construction and unreliable in performance" (harrypotter.com, "Mr Ollivander").
 * Unicorn hair gives "the most consistent magic", dragon heartstring is "the most prone of the three cores to
 * accidents, being somewhat temperamental", and phoenix feather is "the hardest to tame" (harrypotter.com, "Wand
 * cores"). That is what {@link CoreMaking.Pulse} plays during the marrying. A maker tests a wand by its first spell
 * (GoF ch.18, "The Weighing of the Wands"), the first sparks that end a making.
 */
@NullMarked
public final class BenchCraft {

    private BenchCraft() {}

    /** Where a making stands. {@link #NONE}: no job; the bench is bare or only holds materials. */
    public enum Stage {
        NONE, SEATED, TURNED, BORED, MARRIED;

        public String key() {
            return "wandcraft.bench.stage." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What failing an act can cost. */
    public enum Risk {
        /** The blank splits along the grain: it is lost, the job ends, and the core stays on the bench. */
        CRACK,
        /** The core does not take: it is spent, and the bored blank waits for another core. */
        CORE_LOST,
        NONE
    }

    /** How an act is played: taps on a beat, or holding a marker in a band. */
    public enum Kind { TAPS, HOLD }

    /** One act at the bench. Each moves a making from {@link #from} to the next stage; {@link #SEAL} finishes it. */
    public enum Act {
        TURN(Stage.SEATED, Risk.CRACK, Kind.TAPS, BenchTool.CHISEL, BenchTool.KNIFE),
        BORE(Stage.TURNED, Risk.CRACK, Kind.HOLD, BenchTool.BRACE, null),
        MARRY(Stage.BORED, Risk.CORE_LOST, Kind.TAPS, BenchTool.TONGS, null),
        SEAL(Stage.MARRIED, Risk.NONE, Kind.TAPS, BenchTool.VARNISH, null);

        private final Stage from;
        private final Risk risk;
        private final Kind kind;
        private final BenchTool tool;
        private final @Nullable BenchTool helper;

        Act(Stage from, Risk risk, Kind kind, BenchTool tool, @Nullable BenchTool helper) {
            this.from = from;
            this.risk = risk;
            this.kind = kind;
            this.tool = tool;
            this.helper = helper;
        }

        public Stage from() { return from; }
        public Risk risk() { return risk; }
        public Kind kind() { return kind; }
        /** The tool that widens this act's window. */
        public BenchTool tool() { return tool; }
        /** A second tool that helps a little (the knife pares the blank before it is turned), or none. */
        public @Nullable BenchTool helper() { return helper; }

        /** The stage this act leaves the making at; {@code null} for {@link #SEAL}, which ends it. */
        public @Nullable Stage to() {
            return this == SEAL ? null : Stage.values()[from.ordinal() + 1];
        }

        public String key() {
            return "wandcraft.bench.act." + name().toLowerCase(Locale.ROOT);
        }

        /** The act a making at {@code stage} does next. A bare bench seats its blank and turns it in one go. */
        public static Act next(Stage stage) {
            return switch (stage) {
                case NONE, SEATED -> TURN;
                case TURNED -> BORE;
                case BORED -> MARRY;
                case MARRIED -> SEAL;
            };
        }
    }

    // ── the maker ────────────────────────────────────────────────────────────

    /**
     * The Wandlore nodes that count as knowing the craft, in the order the vocation's pathway teaches them
     * ({@code vocations/wandlore.json}). Each one held is a little skill of mind.
     */
    public static final List<String> LORE_NODES = List.of("wand_study", "wand_appraisal", "wandwood_grain",
            "wandlore_figures", "allegiance_grip", "wand_true_master", "wandkeepers_hands");

    /**
     * Who stands at the bench, as the rules need it.
     *
     * @param wandmaker       committed to the Wandlore vocation (it grants {@code wand_care_for_others})
     * @param careerWandmaker the Wandmaker career
     * @param loreNodes       {@link #LORE_NODES} held
     */
    public record Hands(boolean wandmaker, boolean careerWandmaker, int loreNodes) {
        public static final Hands NOVICE = new Hands(false, false, 0);
        public static final Hands MASTER = new Hands(true, true, LORE_NODES.size());

        /** Skill of mind, 0 to 1: the vocation is most of it, the career and the lore the rest. */
        public float skill() {
            float s = (wandmaker ? 0.45f : 0f) + (careerWandmaker ? 0.20f : 0f)
                    + 0.05f * Math.max(0, Math.min(LORE_NODES.size(), loreNodes));
            return Math.min(1.0f, s);
        }

        /**
         * Misses a Wandmaker is forgiven per act: one, two for a master of the craft. A non-maker is forgiven
         * nothing (owner: "pretty difficult for those who are not wandmakers").
         */
        public int forgive() {
            if (!wandmaker) {
                return 0;
            }
            return skill() >= 0.8f ? 2 : 1;
        }
    }

    // ── the odds ─────────────────────────────────────────────────────────────

    /** Beats in a tapped act. */
    public static final int TURN_STROKES = 6;
    public static final int MARRY_PULSES = 7;
    public static final int SEAL_STROKES = 4;
    /** Ticks before the first beat, so the player can find the rhythm. */
    public static final int LEAD = 30;
    /** Ticks a hold act runs after its lead. */
    public static final int BORE_TICKS = 160;
    /** A score at or above this makes a clean wand. Below it the wand is flawed. */
    public static final float CLEAN = 0.8f;
    /** The integrity of a wand made with every act failed. */
    public static final float WORST_INTEGRITY = 0.5f;

    /**
     * Everything an act is measured against, fixed when it begins and sent to the player's client, so both sides
     * build the same beat.
     *
     * @param hardness 0 (easy) to 1, from the wood for turning and boring and from the core for marrying
     * @param ease     0 to 1: skill, tools and affinity against hardness
     * @param window   ticks either side of a beat that still count as a hit
     * @param forgive  misses forgiven
     * @param band     half the width of the hold band, as a share of the bar (boring)
     * @param risk     the chance of {@link Act#risk()} if every beat is missed; a perfect act risks nothing
     * @param pulse    the rhythm marrying plays (the core's, calmed by affinity); a steady stroke for the others
     */
    public record Odds(Act act, float hardness, float ease, int window, int forgive, float band, float risk,
                       CoreMaking.Pulse pulse) {}

    /** The steady stroke of the lathe and the varnish brush. Its tempo is set by {@link #odds}. */
    private static final CoreMaking.Pulse STEADY = new CoreMaking.Pulse(20, 0f, 0f);

    /**
     * The odds of one act.
     *
     * @param tools          the tools on the bench, as {@link BenchTool#bit()}s
     * @param woodDifficulty {@link WoodMaking#difficulty()}, 2.0 (holly) to 6.7 (elder)
     * @param core           the core's making, for marrying; any other act ignores it
     * @param affinity       {@link WandmakingSpec#affinity()}
     */
    public static Odds odds(Act act, Hands hands, int tools, float woodDifficulty, CoreMaking core, int affinity) {
        float hardness = switch (act) {
            case TURN, BORE -> clamp((woodDifficulty - 2.0f) / 5.0f, 0f, 1f);
            case MARRY -> clamp(core.difficulty() / 3.5f - 0.15f * affinity, 0f, 1f);
            case SEAL -> 0f;
        };
        float toolBonus = ((tools & act.tool().bit()) != 0 ? 0.15f : 0f)
                + (act.helper() != null && (tools & act.helper().bit()) != 0 ? 0.05f : 0f);
        float skill = hands.skill();
        float ease = clamp(0.2f + 0.5f * skill + toolBonus - 0.3f * hardness, 0.05f, 1f);
        int window = 1 + Math.round(ease * 3f) + (act == Act.SEAL ? 1 : 0);
        float band = 0.08f + 0.17f * ease;
        float risk = act.risk() == Risk.NONE ? 0f
                : clamp(0.08f + 0.30f * hardness - 0.12f * skill - (toolBonus > 0f ? 0.04f : 0f), 0.02f, 0.40f);
        CoreMaking.Pulse pulse = switch (act) {
            case MARRY -> core.pulse().calmedBy(affinity);
            case TURN -> new CoreMaking.Pulse(Math.round(22 - 6 * hardness), 0f, 0f);
            case SEAL -> new CoreMaking.Pulse(24, 0f, 0f);
            case BORE -> STEADY;
        };
        return new Odds(act, round3(hardness), round3(ease), window, hands.forgive(), round3(band), round3(risk), pulse);
    }

    /** The chance the act's {@link Risk} strikes after a performance scored {@code score}. */
    public static float chance(Odds odds, float score) {
        return odds.risk() * (1f - clamp(score, 0f, 1f));
    }

    /**
     * The integrity of a finished wand from the mean of its acts' scores. {@link #CLEAN} and above is a clean wand
     * (1.0). Below, it falls to {@link #WORST_INTEGRITY}, and the existing integrity path makes a flawed wand fizzle
     * now and then, "unreliable in performance".
     */
    public static float integrity(float meanScore) {
        if (meanScore >= CLEAN) {
            return 1.0f;
        }
        float t = clamp(meanScore, 0f, CLEAN) / CLEAN;
        return Math.round((WORST_INTEGRITY + (1f - WORST_INTEGRITY) * t) * 100f) / 100f;
    }

    // ── the beat ─────────────────────────────────────────────────────────────

    /**
     * One act's beat. For a tapped act {@link #beats} are the ticks to press on; for a hold act the band is
     * simulated by {@link #bore}.
     */
    public record Beat(Act act, int duration, int[] beats) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Beat b && b.act == act && b.duration == duration && Arrays.equals(b.beats, beats);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * act.hashCode() + duration) + Arrays.hashCode(beats);
        }

        @Override
        public String toString() {
            return "Beat[" + act + ", " + duration + ", " + Arrays.toString(beats) + "]";
        }
    }

    /** The beat of one act, from its odds and a seed. The same on every side that has both. */
    public static Beat beat(Odds odds, long seed) {
        Act act = odds.act();
        if (act.kind() == Kind.HOLD) {
            return new Beat(act, LEAD + BORE_TICKS, new int[0]);
        }
        int count = switch (act) {
            case TURN -> TURN_STROKES;
            case MARRY -> MARRY_PULSES;
            default -> SEAL_STROKES;
        };
        Random random = new Random(seed);
        CoreMaking.Pulse pulse = odds.pulse();
        double phase = random.nextDouble() * Math.PI * 2;
        int[] beats = new int[count];
        int t = LEAD;
        for (int i = 0; i < count; i++) {
            beats[i] = t;
            // Drift bends the tempo over the act (phoenix: fast and shifting); jitter throws single beats early or
            // late (dragon: flares). Both are bounded, so a beat always comes and never two at once.
            double drift = 1.0 + pulse.drift() * 0.45 * StrictMath.sin(phase + i * 0.9);
            double jitter = pulse.jitter() * 0.5 * (random.nextDouble() * 2 - 1);
            t += Math.max(6, (int) Math.round(pulse.tempo() * (drift + jitter)));
        }
        return new Beat(act, beats[count - 1] + Math.max(12, pulse.tempo()), beats);
    }

    /**
     * Scores taps against a beat: each beat is hit by the first unused tap within {@link Odds#window} ticks of it.
     * Taps that hit nothing count as half a miss each, so hammering the key is no way through. Then the maker's
     * {@link Odds#forgive} misses are taken off.
     */
    public static float scoreTaps(Beat beat, Odds odds, int[] taps) {
        int[] sorted = taps.clone();
        Arrays.sort(sorted);
        boolean[] used = new boolean[sorted.length];
        int hits = 0;
        for (int b : beat.beats()) {
            for (int i = 0; i < sorted.length; i++) {
                if (!used[i] && Math.abs(sorted[i] - b) <= odds.window()) {
                    used[i] = true;
                    hits++;
                    break;
                }
            }
        }
        int beats = beat.beats().length;
        float misses = (beats - hits) + 0.5f * (sorted.length - hits);
        misses = Math.max(0f, misses - odds.forgive());
        return clamp(1f - misses / beats, 0f, 1f);
    }

    /** One tick of the hold act, for drawing it. */
    public record BoreFrame(float marker, float center, boolean inside) {}

    /**
     * Plays the hold act: the brace's bit (the marker) wants to sink, holding the use key lifts it, the grain kicks
     * it about, and the band it must stay in wanders. Returns one frame per tick from the lead on; {@code held[i]} is
     * whether the key was down on tick {@code LEAD + i}. The client draws these frames; the server scores them.
     */
    public static BoreFrame[] bore(Odds odds, long seed, boolean[] held) {
        Random random = new Random(seed);
        double phase = random.nextDouble() * Math.PI * 2;
        float speed = 0.02f + 0.03f * odds.hardness();
        float grain = 0.004f * (1f + odds.hardness());
        float marker = 0.5f;
        float velocity = 0f;
        BoreFrame[] frames = new BoreFrame[BORE_TICKS];
        for (int i = 0; i < BORE_TICKS; i++) {
            float center = 0.5f + 0.3f * (float) StrictMath.sin(phase + i * speed);
            boolean down = i < held.length && held[i];
            velocity += (random.nextFloat() - 0.5f) * grain;
            velocity += down ? 0.0035f : -0.0030f;
            velocity *= 0.92f;
            marker += velocity;
            if (marker < 0f || marker > 1f) {
                marker = clamp(marker, 0f, 1f);
                velocity = 0f;
            }
            frames[i] = new BoreFrame(marker, center, Math.abs(marker - center) <= odds.band());
        }
        return frames;
    }

    /** Scores the hold act: the share of ticks the marker stayed in the band, plus 5% per forgiven miss. */
    public static float scoreBore(Odds odds, long seed, boolean[] held) {
        BoreFrame[] frames = bore(odds, seed, held);
        int inside = 0;
        for (BoreFrame f : frames) {
            if (f.inside()) {
                inside++;
            }
        }
        return clamp((float) inside / frames.length + 0.05f * odds.forgive(), 0f, 1f);
    }

    /** Scores either kind of act. {@code held} is indexed from the end of the lead. */
    public static float score(Odds odds, long seed, int[] taps, boolean[] held) {
        return odds.act().kind() == Kind.HOLD ? scoreBore(odds, seed, held) : scoreTaps(beat(odds, seed), odds, taps);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float round3(float v) {
        return Math.round(v * 1000f) / 1000f;
    }
}
