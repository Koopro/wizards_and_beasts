package at.koopro.wizardsandbeasts.owl;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ProfessionEligibility {

    private ProfessionEligibility() {}

    public static boolean isEligible(
            @NonNull ServerPlayer player,
            @NonNull Profession profession,
            @NonNull Map<OWLSubject, OWLGrade> grades) {
        return switch (profession) {
            case AUROR -> meetsAll(grades,
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E),
                    req(OWLSubject.POTIONS, OWLGrade.E),
                    req(OWLSubject.TRANSFIGURATION, OWLGrade.E),
                    req(OWLSubject.CHARMS, OWLGrade.E),
                    req(OWLSubject.HERBOLOGY, OWLGrade.E));
            case HEALER -> meetsAll(grades,
                    req(OWLSubject.TRANSFIGURATION, OWLGrade.E),
                    req(OWLSubject.POTIONS, OWLGrade.E),
                    req(OWLSubject.HERBOLOGY, OWLGrade.E),
                    req(OWLSubject.CHARMS, OWLGrade.E),
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E));
            case WANDMAKER -> meetsAll(grades,
                    req(OWLSubject.ANCIENT_RUNES, OWLGrade.A),
                    req(OWLSubject.CHARMS, OWLGrade.A));
            case MAGIZOOLOGIST -> meetsAll(grades,
                    req(OWLSubject.CARE_OF_MAGICAL_CREATURES, OWLGrade.A),
                    req(OWLSubject.CHARMS, OWLGrade.A));
            case CURSE_BREAKER -> {
                // Goblin heritage grants +1 effective grade to ARITHMANCY and ANCIENT_RUNES
                Heritage heritage = HeritageAPI.getPlayerHeritage(player);
                boolean goblin = heritage == Heritage.GOBLIN;
                OWLGrade arithRequired = goblin ? OWLGrade.A : OWLGrade.E;
                OWLGrade runesRequired = goblin ? OWLGrade.A : OWLGrade.E;
                yield meetsAll(grades,
                        req(OWLSubject.ARITHMANCY, arithRequired),
                        req(OWLSubject.ANCIENT_RUNES, runesRequired));
            }
            case POTIONEER -> meetsAll(grades,
                    req(OWLSubject.POTIONS, OWLGrade.O),
                    req(OWLSubject.HERBOLOGY, OWLGrade.E));
            case HIT_WIZARD -> meetsAll(grades,
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.O),
                    req(OWLSubject.TRANSFIGURATION, OWLGrade.E),
                    req(OWLSubject.CHARMS, OWLGrade.E));
            case OBLIVIATOR -> meetsAll(grades,
                    req(OWLSubject.CHARMS, OWLGrade.O),
                    req(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E));
            case UNSPEAKABLE -> meetsAll(grades,
                    req(OWLSubject.ARITHMANCY, OWLGrade.O),
                    req(OWLSubject.ANCIENT_RUNES, OWLGrade.O),
                    req(OWLSubject.TRANSFIGURATION, OWLGrade.O));
            case PROFESSOR -> {
                // Any subject at Outstanding
                yield grades.values().stream().anyMatch(g -> g == OWLGrade.O);
            }
            case QUIDDITCH_PLAYER, JOURNALIST -> true;
            case ARITHMANCER -> meetsAll(grades,
                    req(OWLSubject.ARITHMANCY, OWLGrade.O),
                    req(OWLSubject.ANCIENT_RUNES, OWLGrade.E));
            case HERBOLOGIST -> meetsAll(grades,
                    req(OWLSubject.HERBOLOGY, OWLGrade.O),
                    req(OWLSubject.POTIONS, OWLGrade.E));
        };
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

    private record Requirement(OWLSubject subject, OWLGrade minGrade) {}

    private static Requirement req(OWLSubject subject, OWLGrade minGrade) {
        return new Requirement(subject, minGrade);
    }

    private static boolean meetsAll(Map<OWLSubject, OWLGrade> grades, Requirement... reqs) {
        for (Requirement r : reqs) {
            OWLGrade actual = grades.getOrDefault(r.subject(), OWLGrade.T);
            if (actual.value < r.minGrade().value) return false;
        }
        return true;
    }
}
