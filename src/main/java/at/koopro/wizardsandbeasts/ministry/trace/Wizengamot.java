package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 *
 * <p>The court is held to the evidence: a contested charge whose evidence falls short of
 * {@link EvidenceRules#standardFor} is dismissed, whatever the wizard actually did. A guilty plea is evidence and
 * earns a lighter ruling. Standing with the Ministry is a character reference. Surrendering to the Aurors is
 * better than being caught, and answering the summons better than both.
 */
@NullMarked
public final class Wizengamot {

    /** Wand held for a day: a first real penalty. */
    public static final long CONFISCATION_SHORT_TICKS = 24000L;
    /** Three days, for dark magic. */
    public static final long CONFISCATION_MEDIUM_TICKS = 72000L;
    /** A week, alongside an Azkaban referral. */
    public static final long CONFISCATION_LONG_TICKS = 168000L;
    /** The shortest a licence is suspended for when a ruling suspends one. */
    public static final long SUSPENSION_MIN_TICKS = 72000L;
    /** What a guilty plea, or a good character, takes off a fine — each, in percent. */
    public static final int FINE_DISCOUNT_PERCENT = 25;

    private Wizengamot() {}

    /**
     * One charge before the court.
     *
     * @param fromWandExamination found by examining the wand at the hearing, not reported at the time
     * @param strength            the evidence on it, {@link EvidenceRules#strength}
     * @param licensedPractice    the wizard held a Restricted Magic licence when it was cast
     */
    public record Charge(LegalClass legalClass, Exposure exposure, boolean underageBreach, boolean inDanger,
                         Optional<MagicalOffence> offence, boolean fromWandExamination, int strength,
                         boolean licensedPractice) {

        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LegalClass.CODEC.fieldOf("legal_class").forGetter(Charge::legalClass),
                Exposure.CODEC.fieldOf("exposure").forGetter(Charge::exposure),
                Codec.BOOL.optionalFieldOf("underage", false).forGetter(Charge::underageBreach),
                Codec.BOOL.optionalFieldOf("in_danger", false).forGetter(Charge::inDanger),
                MagicalOffence.CODEC.optionalFieldOf("offence").forGetter(Charge::offence),
                Codec.BOOL.optionalFieldOf("wand_examination", false).forGetter(Charge::fromWandExamination),
                Codec.INT.optionalFieldOf("strength", EvidenceRules.MAX_STRENGTH).forGetter(Charge::strength),
                Codec.BOOL.optionalFieldOf("licensed", false).forGetter(Charge::licensedPractice)
        ).apply(instance, Charge::new));

        /** A charge proven beyond question, with no licence — how every charge read before evidence existed. */
        public Charge(LegalClass legalClass, Exposure exposure, boolean underageBreach, boolean inDanger,
                      Optional<MagicalOffence> offence, boolean fromWandExamination) {
            this(legalClass, exposure, underageBreach, inDanger, offence, fromWandExamination,
                    EvidenceRules.MAX_STRENGTH, false);
        }

        public static Charge of(Incident incident, boolean fromWandExamination, int strength, boolean licensed) {
            return new Charge(incident.legalClass(), incident.exposure(), incident.underageBreach(),
                    incident.inDanger(), incident.offence(), fromWandExamination, strength, licensed);
        }

        /** Whether the evidence on this charge is conclusive whatever the court's bar: the wand itself. */
        public boolean conclusive() {
            return fromWandExamination;
        }
    }

    /**
     * @param underageWarnings    underage warning letters already sent
     * @param secrecyNotes        secrecy breaches on file, including these charges
     * @param appearedVoluntarily answered the summons rather than being brought in by Aurors
     * @param plea                how the wizard answers
     * @param goodStanding        the wizard's Ministry standing is at least leaning positive
     * @param standardBonus       added to every standard of proof; an appeal court's {@link EvidenceRules#APPEAL_BONUS}
     * @param surrendered         gave themselves up after the summons lapsed, rather than being caught
     */
    public record Hearing(List<Charge> charges, int underageWarnings, int secrecyNotes,
                          boolean appearedVoluntarily, Plea plea, boolean goodStanding, int standardBonus,
                          boolean surrendered) {

        public static final Codec<Hearing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Charge.CODEC.listOf().optionalFieldOf("charges", List.of()).forGetter(Hearing::charges),
                Codec.INT.optionalFieldOf("underage_warnings", 0).forGetter(Hearing::underageWarnings),
                Codec.INT.optionalFieldOf("secrecy_notes", 0).forGetter(Hearing::secrecyNotes),
                Codec.BOOL.optionalFieldOf("appeared", true).forGetter(Hearing::appearedVoluntarily),
                Plea.CODEC.optionalFieldOf("plea", Plea.CONTEST).forGetter(Hearing::plea),
                Codec.BOOL.optionalFieldOf("good_standing", false).forGetter(Hearing::goodStanding),
                Codec.INT.optionalFieldOf("standard_bonus", 0).forGetter(Hearing::standardBonus),
                Codec.BOOL.optionalFieldOf("surrendered", false).forGetter(Hearing::surrendered)
        ).apply(instance, Hearing::new));

        public Hearing {
            charges = List.copyOf(charges);
        }

        /** A contested first hearing with no character reference. */
        public Hearing(List<Charge> charges, int underageWarnings, int secrecyNotes, boolean appearedVoluntarily) {
            this(charges, underageWarnings, secrecyNotes, appearedVoluntarily, Plea.CONTEST, false, 0, false);
        }

        /** The same hearing heard again on appeal: contested, at a higher bar, with today's character. */
        public Hearing onAppeal(boolean goodStandingNow) {
            return new Hearing(charges, underageWarnings, secrecyNotes, appearedVoluntarily, Plea.CONTEST,
                    goodStandingNow, standardBonus + EvidenceRules.APPEAL_BONUS, surrendered);
        }
    }

    /**
     * @param reasons           lang suffixes under {@code ministry.wizards_and_beasts.hearing.reason.}, in order
     * @param filed             the offence entered on the file, if any
     * @param confiscationTicks how long the wand is held; 0 for none
     * @param finePercent       how much of the filed offence's tariff is billed, 100 for all of it
     * @param suspensions       licences suspended by the ruling
     * @param suspensionTicks   how long they are suspended; 0 when none are
     */
    public record Ruling(Verdict verdict, List<String> reasons, Optional<MagicalOffence> filed,
                         long confiscationTicks, int finePercent, List<LicenseType> suspensions,
                         long suspensionTicks) {

        public static final Codec<Ruling> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Verdict.CODEC.fieldOf("verdict").forGetter(Ruling::verdict),
                Codec.STRING.listOf().optionalFieldOf("reasons", List.of()).forGetter(Ruling::reasons),
                MagicalOffence.CODEC.optionalFieldOf("filed").forGetter(Ruling::filed),
                Codec.LONG.optionalFieldOf("confiscation", 0L).forGetter(Ruling::confiscationTicks),
                Codec.INT.optionalFieldOf("fine_percent", 100).forGetter(Ruling::finePercent),
                LicenseType.CODEC.listOf().optionalFieldOf("suspensions", List.of()).forGetter(Ruling::suspensions),
                Codec.LONG.optionalFieldOf("suspension", 0L).forGetter(Ruling::suspensionTicks)
        ).apply(instance, Ruling::new));

        public Ruling {
            reasons = List.copyOf(reasons);
            suspensions = List.copyOf(suspensions);
            finePercent = Math.max(0, Math.min(100, finePercent));
        }

        public Ruling(Verdict verdict, List<String> reasons, Optional<MagicalOffence> filed, long confiscationTicks) {
            this(verdict, reasons, filed, confiscationTicks, 100, List.of(), 0L);
        }

        /**
         * How heavy this ruling is, for comparing a ruling with its appeal: the verdict first, then how long the
         * wand is held, then how much of the fine is billed.
         */
        public long severity() {
            return verdict.ordinal() * 1_000_000_000L + confiscationTicks * 1000L + finePercent;
        }
    }

    public static Ruling rule(Hearing hearing) {
        List<String> reasons = new ArrayList<>();
        List<Charge> standing = new ArrayList<>();
        boolean guilty = hearing.plea() == Plea.GUILTY;
        for (Charge charge : hearing.charges()) {
            if (!guilty && !charge.conclusive()
                    && !EvidenceRules.meetsStandard(charge.strength(), charge.legalClass(), hearing.standardBonus())) {
                addOnce(reasons, "insufficient_evidence");
            } else if (mitigatedByDanger(charge)) {
                addOnce(reasons, "mitigated_danger");
            } else if (licensedPractice(charge)) {
                addOnce(reasons, "licensed_practice");
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
        List<LicenseType> suspensions = new ArrayList<>();
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
            suspensions.add(LicenseType.RESTRICTED_MAGIC);
            suspensions.add(LicenseType.AUROR_TRAINEE);
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

        // Leniency is for what is short of Azkaban: neither a plea nor a reputation buys down an Unforgivable.
        int finePercent = 100;
        if (verdict != Verdict.AZKABAN_REFERRAL) {
            if (guilty) {
                reasons.add("pleaded_guilty");
                finePercent -= FINE_DISCOUNT_PERCENT;
                confiscation /= 2;
            }
            boolean answered = hearing.appearedVoluntarily() || hearing.surrendered();
            if (hearing.goodStanding() && answered) {
                reasons.add("good_standing");
                finePercent -= FINE_DISCOUNT_PERCENT;
                confiscation = confiscation * 3 / 4;
            }
        }

        if (!hearing.appearedVoluntarily()) {
            if (hearing.surrendered()) {
                // Gave themselves up: the lapse is on the file, but they are not treated as caught.
                reasons.add("surrendered");
            } else {
                reasons.add("failed_to_appear");
                confiscation = Math.max(confiscation, CONFISCATION_SHORT_TICKS);
                if (verdict == Verdict.WARNING) {
                    verdict = Verdict.WAND_CONFISCATION;
                }
            }
        }

        if (verdict == Verdict.AZKABAN_REFERRAL) {
            suspensions.clear();
            suspensions.add(LicenseType.APPARITION);
            suspensions.add(LicenseType.RESTRICTED_MAGIC);
            suspensions.add(LicenseType.AUROR_TRAINEE);
            suspensions.add(LicenseType.DANGEROUS_CREATURES);
        } else if (verdict == Verdict.WAND_CONFISCATION && confiscation > 0L
                && !suspensions.contains(LicenseType.APPARITION)) {
            suspensions.add(LicenseType.APPARITION);
        }
        long suspension = suspensions.isEmpty() ? 0L : Math.max(SUSPENSION_MIN_TICKS, confiscation);
        return new Ruling(verdict, reasons, filed, confiscation, finePercent, suspensions, suspension);
    }

    private static void addOnce(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
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

    /**
     * A Restricted Magic licence covers dark magic short of the Unforgivables, worked where no Muggle could see —
     * and nothing else. It is not a licence to break the Statute, nor to curse a child's way out of the Trace.
     */
    private static boolean licensedPractice(Charge charge) {
        return charge.licensedPractice()
                && charge.legalClass() == LegalClass.DARK
                && !charge.underageBreach()
                && !charge.exposure().breachesSecrecy();
    }
}
