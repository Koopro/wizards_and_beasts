package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.trace.ClosedCase;
import at.koopro.wizardsandbeasts.ministry.trace.Plea;
import at.koopro.wizardsandbeasts.ministry.trace.Verdict;
import at.koopro.wizardsandbeasts.ministry.trace.Wizengamot;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Applies {@link LawReputationRules} to a player. The one place a hearing or an appeal touches standing, so the
 * Wizengamot's code never has to know which axes exist.
 */
@NullMarked
public final class LawReputation {

    private LawReputation() {}

    /** A hearing has ruled. */
    public static void onRuling(ServerPlayer wizard, Wizengamot.Hearing hearing, Wizengamot.Ruling ruling) {
        MinistryRecords.mutate(wizard, record -> record.withConductAdjusted(LawReputationRules.conductFor(
                hearing.appearedVoluntarily(), hearing.surrendered(), hearing.plea() == Plea.GUILTY)));
        if (ruling.verdict() == Verdict.DISMISSED) {
            StandingService.adjust(wizard, StandingAxis.PUBLIC, LawReputationRules.ACQUITTAL_CREDIT);
            return;
        }
        StandingService.adjust(wizard, StandingAxis.PUBLIC, -LawReputationRules.publicPenalty(ruling.verdict()));
        StandingService.adjust(wizard, StandingAxis.PROFESSION,
                -LawReputationRules.professionPenalty(ruling.verdict(), profession(wizard)));
    }

    /** An appeal has been decided; {@code before} is the ruling it was lodged against. */
    public static void onAppeal(ServerPlayer wizard, ClosedCase.Appeal outcome, Wizengamot.Ruling before,
                                Wizengamot.Ruling after) {
        if (outcome == ClosedCase.Appeal.UPHELD || outcome == ClosedCase.Appeal.NONE) {
            return;
        }
        MinistryRecords.mutate(wizard, record -> record.withConductAdjusted(LawReputationRules.CONDUCT_VINDICATED));
        StandingService.adjust(wizard, StandingAxis.PUBLIC,
                LawReputationRules.publicRestored(before.verdict(), after.verdict()));
        Profession profession = profession(wizard);
        StandingService.adjust(wizard, StandingAxis.PROFESSION, Math.max(0.0f,
                LawReputationRules.professionPenalty(before.verdict(), profession)
                        - LawReputationRules.professionPenalty(after.verdict(), profession)));
    }

    /** Whether the wizard's Ministry standing is a character reference. */
    public static boolean goodStanding(ServerPlayer wizard) {
        return LawReputationRules.goodStanding(StandingService.bandOf(wizard, StandingAxis.MINISTRY));
    }

    /** Whether the wizard is believed as a witness. */
    public static boolean credible(ServerPlayer wizard) {
        return LawReputationRules.credibleWitness(StandingService.bandOf(wizard, StandingAxis.PUBLIC));
    }

    private static @Nullable Profession profession(ServerPlayer wizard) {
        return wizard.getData(ModAttachments.PROFESSION_DATA.get()).profession();
    }
}
