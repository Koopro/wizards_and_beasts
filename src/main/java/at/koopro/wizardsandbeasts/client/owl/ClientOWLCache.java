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
    @Nullable
    private static Profession profession = null;

    private ClientOWLCache() {}

    public static void setOWLData(@NonNull Map<OWLSubject, OWLGrade> newGrades, boolean taken) {
        EnumMap<OWLSubject, OWLGrade> map = new EnumMap<>(OWLSubject.class);
        map.putAll(newGrades);
        grades = Collections.unmodifiableMap(map);
        examTaken = taken;
    }

    public static void setProfession(@Nullable Profession p) {
        profession = p;
    }

    public static @NonNull Map<OWLSubject, OWLGrade> getGrades() {
        return grades;
    }

    public static boolean isExamTaken() {
        return examTaken;
    }

    @Nullable
    public static Profession getProfession() {
        return profession;
    }
}
