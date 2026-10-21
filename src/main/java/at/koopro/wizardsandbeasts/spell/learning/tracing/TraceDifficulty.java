package at.koopro.wizardsandbeasts.spell.learning.tracing;

import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.learning.SpellLawLearningGate;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * How hard the tracing challenge for a spell is.
 *
 * <p>Every number that makes one row harder than another lives here, in one table, so a balance pass is
 * an edit to this file and nothing else. All distances are in canvas units: the canvas is the unit
 * square, so {@code 0.075} is 7.5% of its width.
 *
 * <p>Rows are ordered: each is at least as narrow and at least as strict on accuracy as the one before
 * ({@code TraceDifficultyTest} pins that). Difficulty comes from <em>what the path asks of the hand</em> —
 * corridor width, number and sharpness of turns, accuracy, time — and never from visual noise.
 */
@NullMarked
public enum TraceDifficulty {
    /** Lumos, Alohomora: one gentle stroke, a wide corridor, an unhurried pace. */
    BASIC("basic", 0.075, 0.16, 0.80, 0.9, 1.8, 0,
            List.of(TraceTemplate.ARC, TraceTemplate.SWISH, TraceTemplate.WAVE_1, TraceTemplate.HOOK)),
    /** A spell that builds on another: a few turns, a little narrower. */
    INTERMEDIATE("intermediate", 0.062, 0.20, 0.72, 1.0, 2.2, 0,
            List.of(TraceTemplate.WAVE_2, TraceTemplate.ZIGZAG_2, TraceTemplate.RUNE_4, TraceTemplate.HOOK)),
    /** Gated on a skill, a profession or proficiency in a prerequisite. */
    ADVANCED("advanced", 0.054, 0.23, 0.66, 1.1, 2.6, 100,
            List.of(TraceTemplate.WAVE_3, TraceTemplate.ZIGZAG_3, TraceTemplate.RUNE_6)),
    /** Dark Arts: jagged, angular patterns and a brisker pace. */
    DARK("dark", 0.050, 0.26, 0.62, 1.1, 3.0, 200,
            List.of(TraceTemplate.ZIGZAG_4, TraceTemplate.RUNE_6, TraceTemplate.RUNE_7)),
    /** Gated on mastery: the longest paths and the strictest accuracy, at a careful pace. */
    MASTERY("mastery", 0.050, 0.22, 0.60, 1.6, 3.4, 200,
            List.of(TraceTemplate.RUNE_7, TraceTemplate.RUNE_8, TraceTemplate.SPIRAL));

    /** How long the player may look at the path before starting to trace it. Not part of the limit. */
    public static final int START_ALLOWANCE_TICKS = 200;
    /** Fixed part of every time limit: finding the start and committing to the first stroke. */
    public static final int BASE_TIME_TICKS = 40;
    /** Network and frame slack added to every server-side timing check. */
    public static final int LATENCY_SLACK_TICKS = 40;
    /** Fastest believable pointer, in canvas widths per second. Anything quicker was not traced by hand. */
    public static final double MAX_POINTER_SPEED = 4.0;
    /** What the "Steady hand" accessibility option multiplies the corridor and the time limit by. */
    public static final double ASSIST_FACTOR = 1.2;

    private final String key;
    private final double halfWidth;
    private final double pace;
    private final double maxMeanDeviation;
    private final double minLength;
    private final double maxLength;
    private final int retryDelayTicks;
    private final List<TraceTemplate> templates;

    TraceDifficulty(String key, double halfWidth, double pace, double maxMeanDeviation,
                    double minLength, double maxLength, int retryDelayTicks, List<TraceTemplate> templates) {
        this.key = key;
        this.halfWidth = halfWidth;
        this.pace = pace;
        this.maxMeanDeviation = maxMeanDeviation;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.retryDelayTicks = retryDelayTicks;
        this.templates = templates;
    }

    /** Lang-key suffix: {@code spell.wizards_and_beasts.trace.difficulty.<key>}. */
    public String key() {
        return key;
    }

    /** Half the corridor's width: how far from the centre line the pointer may stray. */
    public double halfWidth() {
        return halfWidth;
    }

    /**
     * The pace the time limit is set at, in canvas widths per second. Deliberately slow — a careful hand
     * manages two or three times this — so the clock presses without punishing deliberation.
     */
    public double pace() {
        return pace;
    }

    /**
     * Time to trace a path of {@code length}, counted from the first touch on the start mark: a fixed
     * {@link #BASE_TIME_TICKS} plus the length at this row's pace. Tied to the path rather than fixed per
     * row, so a long rune never gets less time per stroke than a short arc.
     */
    public int timeLimitTicks(double length) {
        return BASE_TIME_TICKS + (int) Math.ceil(length / pace * 20.0);
    }

    /** The largest mean deviation, as a fraction of {@link #halfWidth}, that still passes. */
    public double maxMeanDeviation() {
        return maxMeanDeviation;
    }

    public double minLength() {
        return minLength;
    }

    public double maxLength() {
        return maxLength;
    }

    /**
     * How long after a failed attempt the next one may start. Zero for the two easy rows: re-reading the
     * book is pacing enough, and a beginner should never be made to wait to try Lumos again.
     */
    public int retryDelayTicks() {
        return retryDelayTicks;
    }

    public List<TraceTemplate> templates() {
        return templates;
    }

    /** By ordinal, clamped: safe on a value read off the wire. */
    public static TraceDifficulty byOrdinal(int ordinal) {
        TraceDifficulty[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    /**
     * The row for a spell, read off data every spell already carries — no new JSON field, so a pack's
     * own spell gets a sensible difficulty without knowing this class exists.
     */
    public static TraceDifficulty forSpell(Spell spell) {
        LegalClass legal = SpellLawLearningGate.lawFor(spell).legalClass();
        boolean dark = spell.getCategory() == SpellCategory.DARK_ARTS
                || legal == LegalClass.DARK || legal == LegalClass.UNFORGIVABLE;
        Proficiency highest = highestProficiency(spell.getRequirement());
        boolean mastery = spell.getRequiredMasteryTier() != null || highest == Proficiency.MASTERED;
        boolean gated = notBlank(spell.getEffectiveRequiredSkillId())
                || notBlank(spell.getRequiredProfessionId())
                || highest == Proficiency.PROFICIENT;
        boolean prerequisite = hasPrerequisite(spell.getRequirement());
        return derive(mastery, dark, gated, prerequisite);
    }

    /**
     * The derivation rule, on plain facts so it can be tested without a registry. First match wins:
     * mastery, then the Dark Arts, then a skill/profession/proficiency gate, then a plain prerequisite.
     */
    public static TraceDifficulty derive(boolean mastery, boolean dark, boolean gated, boolean prerequisite) {
        if (mastery) {
            return MASTERY;
        }
        if (dark) {
            return DARK;
        }
        if (gated) {
            return ADVANCED;
        }
        return prerequisite ? INTERMEDIATE : BASIC;
    }

    private static Proficiency highestProficiency(SpellRequirement requirement) {
        Proficiency best = requirement.getMinProficiency();
        for (SpellRequirement clause : requirement.clauses()) {
            Proficiency p = highestProficiency(clause);
            if (p != null && (best == null || p.ordinal() > best.ordinal())) {
                best = p;
            }
        }
        return best == null ? Proficiency.NOVICE : best;
    }

    private static boolean hasPrerequisite(SpellRequirement requirement) {
        if (notBlank(requirement.getPrerequisiteId())) {
            return true;
        }
        for (SpellRequirement clause : requirement.clauses()) {
            if (hasPrerequisite(clause)) {
                return true;
            }
        }
        return false;
    }

    private static boolean notBlank(@org.jspecify.annotations.Nullable String s) {
        return s != null && !s.isBlank();
    }
}
