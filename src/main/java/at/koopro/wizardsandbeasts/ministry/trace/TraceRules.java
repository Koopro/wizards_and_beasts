package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * What a piece of magic amounts to in law, and who comes to hear of it. Pure: a {@link CastScene} and a
 * {@link SpellLaw} in, facts out — no world, no randomness, the same answer every time.
 *
 * <p>The rule the whole class exists for: <b>a cast is not a detection</b>. The Ministry learns of magic
 * through channels — the Trace on an underage wizard, a Muggle who saw something, an official who was there —
 * and each channel is late in its own way and knows only part of the story. An adult casting a hex alone in
 * the wilderness is known to nobody, and nothing here pretends otherwise.
 */
@NullMarked
public final class TraceRules {

    /** How soon the Improper Use of Magic Office hears from the Trace. Harry's warning came within minutes. */
    public static final int TRACE_DELAY_TICKS = 100;
    /** An official who saw it files a report. */
    public static final int OFFICIAL_DELAY_TICKS = 200;
    /** Muggle sightings reach the Obliviators second-hand. */
    public static final int MUGGLE_REPORT_DELAY_TICKS = 1200;
    /** How long an inquiry into an unattributed incident runs before it names someone or goes cold. */
    public static final int INQUIRY_TICKS = 6000;

    /** Muggles who each give an account — enough for an inquiry to put a name to a face. */
    public static final int WITNESSES_TO_IDENTIFY = 2;
    /** A crowd: this many Muggles make even modest magic a severe breach. */
    public static final int CROWD = 4;

    /** Other wizards of age within this many blocks could have cast it themselves. */
    public static final double WIZARD_PRESENCE_RADIUS = 16.0;

    private TraceRules() {}

    /** How far away a witness can be and still notice magic of this visibility. */
    /**
     * {@link #witnessRadius(int)}, narrowed by a caster's trained discretion.
     *
     * <p>Split out as a pure function because it is the one place the Dark Arts web is allowed to act against
     * the Ministry, and "how much quieter may study make you" is exactly the sort of number that should be
     * pinned by a test rather than buried in a survey. Clamped at {@link #MAX_DISCRETION}: a witness standing
     * at your elbow sees you whatever you have read.
     */
    public static double witnessRadius(int visibility, float discretion) {
        double clamped = Math.max(0.0, Math.min(MAX_DISCRETION, discretion));
        return witnessRadius(visibility) * (1.0 - clamped);
    }

    /** The most a trained caster may shrink the circle of people who notice them. */
    public static final float MAX_DISCRETION = 0.6f;

    public static double witnessRadius(int visibility) {
        return switch (Math.max(0, Math.min(SpellLaw.MAX_VISIBILITY, visibility))) {
            case 0 -> 4.0;
            case 1 -> 16.0;
            case 2 -> 32.0;
            default -> 48.0;
        };
    }

    /** Underage magic outside school. At Hogwarts students are expected to use magic. */
    public static boolean underageBreach(CastScene scene) {
        return scene.underage() && !scene.atHogwarts();
    }

    /** How far this cast broke secrecy. */
    public static Exposure exposure(CastScene scene, SpellLaw law) {
        if (scene.atHogwarts()) {
            // Unplottable, and bewitched so a Muggle sees only a ruin.
            return Exposure.NONE;
        }
        if (scene.muggleWitnesses() == 0) {
            return law.visibility() == 0 ? Exposure.NONE : Exposure.LOW;
        }
        if (scene.dangerousCreatureSeen()) {
            return Exposure.EXTREME;
        }
        if (law.visibility() == 0) {
            // A Muggle stood there and saw nothing happen.
            return Exposure.NONE;
        }
        if (law.legalClass().leavesResidue()) {
            return Exposure.EXTREME;
        }
        if ((scene.muggleWitnesses() >= 2 && law.visibility() >= SpellLaw.MAX_VISIBILITY)
                || scene.muggleWitnesses() >= CROWD) {
            return Exposure.SEVERE;
        }
        return Exposure.MODERATE;
    }

    /** A report the Ministry will receive about this cast. */
    public record PlannedReport(ReportChannel channel, int delayTicks, boolean identifiesCaster) {}

    /**
     * Every report this cast produces, in no particular order. Empty means nobody will ever know — unless a
     * wand examination later finds it, see {@link LegalClass#leavesResidue()}.
     */
    public static List<PlannedReport> reports(CastScene scene, SpellLaw law, Exposure exposure) {
        List<PlannedReport> reports = new ArrayList<>(3);
        boolean underageBreach = underageBreach(scene);
        if (underageBreach) {
            // The Trace cannot tell whose magic it felt when grown wizards were standing about.
            reports.add(new PlannedReport(ReportChannel.TRACE, TRACE_DELAY_TICKS, scene.adultWizardsNearby() == 0));
        }
        if (scene.officialsWatching() > 0
                && (underageBreach || exposure.breachesSecrecy() || law.legalClass().leavesResidue())) {
            reports.add(new PlannedReport(ReportChannel.OFFICIAL, OFFICIAL_DELAY_TICKS, true));
        }
        if (exposure.breachesSecrecy()) {
            reports.add(new PlannedReport(ReportChannel.MUGGLE_REPORT, MUGGLE_REPORT_DELAY_TICKS, false));
        }
        return reports;
    }

    /**
     * Whether an incident nobody put a name to is serious enough for the Department to go looking. An
     * underage wizard's minor magic at home with adults about is left to the family, as in canon.
     */
    public static boolean inquiryWarranted(LegalClass legalClass, Exposure exposure) {
        return exposure.atLeast(Exposure.SEVERE) || legalClass.leavesResidue();
    }

    /** Whether an inquiry names the caster: only when enough Muggles gave an account before they were Obliviated. */
    public static boolean inquiryIdentifies(int muggleWitnesses) {
        return muggleWitnesses >= WITNESSES_TO_IDENTIFY;
    }

    /** The weight of an incident, for ordering charges. */
    public static int gravity(LegalClass legalClass, Exposure exposure, boolean underageBreach) {
        return legalClass.gravity() + exposure.gravity() + (underageBreach ? 1 : 0);
    }
}
