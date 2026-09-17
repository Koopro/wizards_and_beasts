package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A disciplinary hearing: the charges and the file in, a verdict and its stated reasons out.
 *
 * <p>Pure and deterministic, and every ruling carries the reasons it rests on, so the letter a wizard receives
 * can say why. The rules follow the two hearings canon shows in detail — Harry's in <i>Order of the
 * Phoenix</i>, where underage magic in the presence of Dementors was cleared under the Decree's
 * life-threatening exception, and the Death Eater trials in <i>Goblet of Fire</i>, where Unforgivables meant
 * Azkaban.
 */
@NullMarked
public final class Wizengamot {

    /** Wand held for a day: a first real penalty. */
    public static final long CONFISCATION_SHORT_TICKS = 24000L;
    /** Three days, for dark magic. */
    public static final long CONFISCATION_MEDIUM_TICKS = 72000L;
    /** A week, alongside an Azkaban referral. */
    public static final long CONFISCATION_LONG_TICKS = 168000L;

    private Wizengamot() {}

    /**
     * One charge before the court.
     *
     * @param fromWandExamination found by examining the wand at the hearing, not reported at the time
     */
    public record Charge(LegalClass legalClass, Exposure exposure, boolean underageBreach, boolean inDanger,
                         Optional<MagicalOffence> offence, boolean fromWandExamination) {

        public static Charge of(Incident incident, boolean fromWandExamination) {
            return new Charge(incident.legalClass(), incident.exposure(), incident.underageBreach(),
                    incident.inDanger(), incident.offence(), fromWandExamination);
        }
    }

    /**
     * @param underageWarnings    underage warning letters already sent
     * @param secrecyNotes        secrecy breaches on file, including these charges
     * @param appearedVoluntarily answered the summons rather than being brought in by Aurors
     */
    public record Hearing(List<Charge> charges, int underageWarnings, int secrecyNotes,
                          boolean appearedVoluntarily) {

        public Hearing {
            charges = List.copyOf(charges);
        }
    }

    /**
     * @param reasons           lang suffixes under {@code ministry.wizards_and_beasts.hearing.reason.}, in order
     * @param filed             the offence entered on the file, if any
     * @param confiscationTicks how long the wand is held; 0 for none
     */
    public record Ruling(Verdict verdict, List<String> reasons, Optional<MagicalOffence> filed,
                         long confiscationTicks) {

        public Ruling {
            reasons = List.copyOf(reasons);
        }
    }

    public static Ruling rule(Hearing hearing) {
        List<String> reasons = new ArrayList<>();
        List<Charge> standing = new ArrayList<>();
        for (Charge charge : hearing.charges()) {
            if (mitigatedByDanger(charge)) {
                if (!reasons.contains("mitigated_danger")) {
                    reasons.add("mitigated_danger");
                }
            } else {
                standing.add(charge);
            }
        }
        if (standing.stream().anyMatch(Charge::fromWandExamination)) {
            reasons.add("wand_examination");
        }
        if (standing.isEmpty()) {
            reasons.add("no_charge");
            return new Ruling(Verdict.DISMISSED, reasons, Optional.empty(), 0L);
        }

        Verdict verdict;
        Optional<MagicalOffence> filed;
        long confiscation;
        Optional<Charge> unforgivable = standing.stream()
                .filter(charge -> charge.legalClass() == LegalClass.UNFORGIVABLE).findFirst();
        boolean underageCaster = standing.stream().anyMatch(Charge::underageBreach);
        if (unforgivable.isPresent()) {
            verdict = Verdict.AZKABAN_REFERRAL;
            filed = unforgivable.get().offence();
            confiscation = CONFISCATION_LONG_TICKS;
            reasons.add("unforgivable");
        } else if (standing.stream().anyMatch(charge -> charge.exposure() == Exposure.EXTREME)) {
            verdict = Verdict.AZKABAN_REFERRAL;
            filed = Optional.of(MagicalOffence.GRAVE_SECRECY_BREACH);
            confiscation = CONFISCATION_LONG_TICKS;
            reasons.add("grave_breach");
        } else if (standing.stream().anyMatch(charge -> charge.legalClass() == LegalClass.DARK)) {
            verdict = Verdict.WAND_CONFISCATION;
            filed = Optional.empty();
            confiscation = CONFISCATION_MEDIUM_TICKS;
            reasons.add("dark_magic");
        } else if (standing.stream().anyMatch(charge -> charge.exposure() == Exposure.SEVERE)
                || (standing.stream().anyMatch(charge -> charge.exposure().breachesSecrecy())
                        && hearing.secrecyNotes() >= CaseRules.BREACHES_BEFORE_HEARING)) {
            verdict = Verdict.FINE;
            filed = Optional.of(MagicalOffence.STATUTE_OF_SECRECY_BREACH);
            confiscation = 0L;
            reasons.add(standing.stream().anyMatch(charge -> charge.exposure() == Exposure.SEVERE)
                    ? "severe_breach" : "repeated_breach");
        } else if (underageCaster && hearing.underageWarnings() > 0) {
            verdict = Verdict.WAND_CONFISCATION;
            filed = Optional.of(MagicalOffence.UNDERAGE_MAGIC);
            confiscation = CONFISCATION_SHORT_TICKS;
            reasons.add("repeated_underage");
        } else if (underageCaster) {
            verdict = Verdict.WARNING;
            filed = Optional.of(MagicalOffence.UNDERAGE_MAGIC);
            confiscation = 0L;
            reasons.add("underage");
        } else {
            verdict = Verdict.WARNING;
            filed = Optional.empty();
            confiscation = 0L;
            reasons.add("minor");
        }

        if (verdict == Verdict.FINE && underageCaster) {
            // A child is not billed; the wand is held instead.
            verdict = Verdict.WAND_CONFISCATION;
            filed = Optional.of(MagicalOffence.UNDERAGE_MAGIC);
            confiscation = CONFISCATION_SHORT_TICKS;
            reasons.add("underage_not_fined");
        }
        if (!hearing.appearedVoluntarily()) {
            reasons.add("failed_to_appear");
            confiscation = Math.max(confiscation, CONFISCATION_SHORT_TICKS);
            if (verdict == Verdict.WARNING) {
                verdict = Verdict.WAND_CONFISCATION;
            }
        }
        return new Ruling(verdict, reasons, filed, confiscation);
    }

    /**
     * The Decree allows underage magic "in life-threatening situations". Only for magic short of the Dark
     * Arts, and not for a spectacle that put a crowd of Muggles in the picture.
     */
    private static boolean mitigatedByDanger(Charge charge) {
        return charge.underageBreach() && charge.inDanger()
                && charge.legalClass().ordinal() <= LegalClass.RESTRICTED.ordinal()
                && !charge.exposure().atLeast(Exposure.SEVERE);
    }
}
