package at.koopro.wizardsandbeasts.client.owl;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Stores the locally synced OWL grades and profession for the client player. */
public final class ClientOWLCache {

    private static Map<OWLSubject, OWLGrade> grades = Map.of();
    private static boolean examTaken = false;
    private static long nextSittingTick = 0L;
    private static boolean careerOpen = false;
    private static Map<OWLSubject, OWLGrade> newtGrades = Map.of();
    private static boolean goblin = false;
    @Nullable
    private static Profession profession = null;

    private ClientOWLCache() {}

    public static void setOWLData(@NonNull Map<OWLSubject, OWLGrade> newGrades, boolean taken,
                                  long nextSitting, boolean careerChoiceOpen,
                                  @NonNull Map<OWLSubject, OWLGrade> newNewtGrades, boolean isGoblin) {
        EnumMap<OWLSubject, OWLGrade> map = new EnumMap<>(OWLSubject.class);
        map.putAll(newGrades);
        grades = Collections.unmodifiableMap(map);
        EnumMap<OWLSubject, OWLGrade> newt = new EnumMap<>(OWLSubject.class);
        newt.putAll(newNewtGrades);
        newtGrades = Collections.unmodifiableMap(newt);
        goblin = isGoblin;
        examTaken = taken;
        nextSittingTick = nextSitting;
        careerOpen = careerChoiceOpen;
    }

    public static void setProfession(@Nullable Profession p) {
        profession = p;
    }

    public static @NonNull Map<OWLSubject, OWLGrade> getGrades() {
        return grades;
    }

    public static @NonNull Map<OWLSubject, OWLGrade> getNewtGrades() {
        return newtGrades;
    }

    /** Whether the server counts this player a goblin (the Curse-Breaker allowance). */
    public static boolean isGoblin() {
        return goblin;
    }

    /** Game time from which the exam may be sat (again); 0 when it may be sat now. */
    public static long getNextSittingTick() {
        return nextSittingTick;
    }

    /** Whether the server will accept a career choice now. */
    public static boolean isCareerOpen() {
        return careerOpen;
    }

    @Nullable
    public static Profession getProfession() {
        return profession;
    }
}
