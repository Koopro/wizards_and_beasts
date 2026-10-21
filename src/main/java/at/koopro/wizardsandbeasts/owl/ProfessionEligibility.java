package at.koopro.wizardsandbeasts.owl;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Which qualifications open which career.
 *
 * <p>The O.W.L. is the gateway: every career but the three open ones asks for O.W.L. grades. The five elite
 * careers — Auror, Healer, Unspeakable, Hit Wizard, Curse-Breaker — also ask for N.E.W.T.s, as canon does
 * (Aurors need "at least five N.E.W.T.s, and not under Exceeds Expectations", OotP ch. 29). The requirements name
 * examined subjects only ({@link OWLSubject#examined()}): a career that asked for Arithmancy or Divination asked
 * for a grade nothing in the game can earn. The Arithmancer is not offered, because its whole subject is not sat.
 * {@code OWLGradeRulesTest} asserts that every offered career is reachable from the shipped data.
 *
 * <p>The pure overload ({@link #isEligible(Profession, Map, Map, boolean)}) is the one the client's selection
 * screen calls too, so the list a player sees and the check the server makes cannot drift.
 */
public final class ProfessionEligibility {

    private ProfessionEligibility() {}

    /** One grade a career asks for, at one level. */
    public record Requirement(OWLSubject subject, OWLGrade minGrade, Qualification level) {
        /** An O.W.L. requirement. */
        public Requirement(OWLSubject subject, OWLGrade minGrade) {
            this(subject, minGrade, Qualification.OWL);
        }

        /** Whether these sheets meet it. */
        public boolean metBy(Map<OWLSubject, OWLGrade> owl, Map<OWLSubject, OWLGrade> newt) {
            if (!subject.examined()) return false;
            OWLGrade actual = (level == Qualification.OWL ? owl : newt).getOrDefault(subject, OWLGrade.T);
            return actual.value >= minGrade.value;
        }
    }

    /** What a career asks for. Empty for a career open to anyone who has sat the exam. */
    public static @NonNull List<Requirement> requirements(@NonNull Profession profession, boolean goblin) {
        return switch (profession) {
            case AUROR -> List.of(
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E),
                    req(OWLSubject.CHARMS, OWLGrade.E),
                    req(OWLSubject.POTIONS, OWLGrade.A),
                    newt(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E),
                    newt(OWLSubject.CHARMS, OWLGrade.A));
            case HEALER -> List.of(
                    req(OWLSubject.POTIONS, OWLGrade.E),
                    req(OWLSubject.HERBOLOGY, OWLGrade.A),
                    req(OWLSubject.CHARMS, OWLGrade.A),
                    newt(OWLSubject.POTIONS, OWLGrade.E),
                    newt(OWLSubject.HERBOLOGY, OWLGrade.A));
            case WANDMAKER -> List.of(
                    req(OWLSubject.ANCIENT_RUNES, OWLGrade.A),
                    req(OWLSubject.CHARMS, OWLGrade.A));
            case MAGIZOOLOGIST -> List.of(
                    req(OWLSubject.CARE_OF_MAGICAL_CREATURES, OWLGrade.E),
                    req(OWLSubject.CHARMS, OWLGrade.A));
            // Goblin heritage: Gringotts trains its own, so the runes bar is one grade lower and no N.E.W.T. is asked.
            case CURSE_BREAKER -> goblin
                    ? List.of(
                            req(OWLSubject.ANCIENT_RUNES, OWLGrade.A),
                            req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.A))
                    : List.of(
                            req(OWLSubject.ANCIENT_RUNES, OWLGrade.E),
                            req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.A),
                            newt(OWLSubject.ANCIENT_RUNES, OWLGrade.A));
            case POTIONEER -> List.of(
                    req(OWLSubject.POTIONS, OWLGrade.O),
                    req(OWLSubject.HERBOLOGY, OWLGrade.A));
            case HIT_WIZARD -> List.of(
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.O),
                    req(OWLSubject.CHARMS, OWLGrade.A),
                    newt(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E));
            case OBLIVIATOR -> List.of(
                    req(OWLSubject.CHARMS, OWLGrade.O),
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.A));
            case UNSPEAKABLE -> List.of(
                    req(OWLSubject.CHARMS, OWLGrade.O),
                    req(OWLSubject.ANCIENT_RUNES, OWLGrade.O),
                    req(OWLSubject.HISTORY_OF_MAGIC, OWLGrade.E),
                    newt(OWLSubject.CHARMS, OWLGrade.E),
                    newt(OWLSubject.ANCIENT_RUNES, OWLGrade.E));
            case HERBOLOGIST -> List.of(
                    req(OWLSubject.HERBOLOGY, OWLGrade.O),
                    req(OWLSubject.POTIONS, OWLGrade.A));
            case ARITHMANCER -> List.of(req(OWLSubject.ARITHMANCY, OWLGrade.O));
            case PROFESSOR, QUIDDITCH_PLAYER, JOURNALIST -> List.of();
        };
    }

    /** Whether a career asks for any N.E.W.T. (for the sheet's "elite" mark). */
    public static boolean asksForNewts(@NonNull Profession profession, boolean goblin) {
        return requirements(profession, goblin).stream().anyMatch(r -> r.level() == Qualification.NEWT);
    }

    /** The requirements these sheets do not meet yet, in the order the career lists them. */
    public static @NonNull List<Requirement> unmet(@NonNull Profession profession, @NonNull Map<OWLSubject, OWLGrade> owl,
                                                   @NonNull Map<OWLSubject, OWLGrade> newt, boolean goblin) {
        List<Requirement> out = new ArrayList<>();
        for (Requirement r : requirements(profession, goblin)) {
            if (!r.metBy(owl, newt)) out.add(r);
        }
        return out;
    }

    /** Pure eligibility against both sheets. Shared by the server check and the client screen. */
    public static boolean isEligible(@NonNull Profession profession, @NonNull Map<OWLSubject, OWLGrade> owl,
                                     @NonNull Map<OWLSubject, OWLGrade> newt, boolean goblin) {
        if (!profession.offered()) return false;
        if (profession == Profession.PROFESSOR) {
            // Any examined subject at Outstanding: you can teach what you were best at.
            return owl.entrySet().stream()
                    .anyMatch(e -> e.getKey().examined() && e.getValue() == OWLGrade.O);
        }
        return unmet(profession, owl, newt, goblin).isEmpty();
    }

    public static boolean isEligible(
            @NonNull ServerPlayer player,
            @NonNull Profession profession,
            @NonNull Map<OWLSubject, OWLGrade> grades) {
        PlayerOWLData record = player.getData(ModAttachments.OWL_DATA.get());
        return isEligible(profession, grades, record.newtGrades(), isGoblin(player));
    }

    public static boolean isGoblin(@NonNull ServerPlayer player) {
        return HeritageAPI.getPlayerHeritage(player) == Heritage.GOBLIN;
    }

    public static @NonNull List<Profession> getEligible(
            @NonNull ServerPlayer player,
            @NonNull Map<OWLSubject, OWLGrade> grades) {
        List<Profession> eligible = new ArrayList<>();
        for (Profession p : Profession.values()) {
            if (isEligible(player, p, grades)) {
                eligible.add(p);
            }
        }
        return Collections.unmodifiableList(eligible);
    }

    private static Requirement req(OWLSubject subject, OWLGrade minGrade) {
        return new Requirement(subject, minGrade);
    }

    private static Requirement newt(OWLSubject subject, OWLGrade minGrade) {
        return new Requirement(subject, minGrade, Qualification.NEWT);
    }
}
