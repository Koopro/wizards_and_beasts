package at.koopro.wizardsandbeasts.owl.data;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A candidate's O.W.L. and N.E.W.T. record.
 *
 * @param grades          best O.W.L. grade per examined subject across every sitting
 * @param examTaken       at least one O.W.L. sitting
 * @param sittings        how many times the O.W.L. has been sat. A save from before resits that says
 *                        {@code examTaken} loads as one sitting.
 * @param lastSatTick     overworld game time of the last O.W.L. sitting; 0 when never, or in a save from before
 *                        resits, which therefore may sit again at once under the current marking
 * @param newtGrades      best N.E.W.T. grade per subject sat at that level; empty in a save from before N.E.W.T.s
 * @param newtSittings    N.E.W.T. sittings
 * @param newtLastSatTick game time of the last N.E.W.T. sitting; 0 when never
 */
public record PlayerOWLData(
        @NonNull Map<OWLSubject, OWLGrade> grades,
        boolean examTaken,
        int sittings,
        long lastSatTick,
        @NonNull Map<OWLSubject, OWLGrade> newtGrades,
        int newtSittings,
        long newtLastSatTick
) {
    private static final Codec<OWLSubject> SUBJECT_CODEC = Codec.STRING.xmap(
            s -> OWLSubject.valueOf(s.toUpperCase()),
            OWLSubject::name);

    private static final Codec<OWLGrade> GRADE_CODEC = Codec.STRING.xmap(
            s -> OWLGrade.valueOf(s.toUpperCase()),
            OWLGrade::name);

    private static final Codec<Map<OWLSubject, OWLGrade>> GRADES_CODEC =
            Codec.unboundedMap(SUBJECT_CODEC, GRADE_CODEC);

    public static final Codec<PlayerOWLData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GRADES_CODEC.optionalFieldOf("grades", Map.of())
                    .forGetter(PlayerOWLData::grades),
            Codec.BOOL.optionalFieldOf("examTaken", false)
                    .forGetter(PlayerOWLData::examTaken),
            Codec.INT.optionalFieldOf("sittings", 0)
                    .forGetter(PlayerOWLData::sittings),
            Codec.LONG.optionalFieldOf("lastSatTick", 0L)
                    .forGetter(PlayerOWLData::lastSatTick),
            GRADES_CODEC.optionalFieldOf("newtGrades", Map.of())
                    .forGetter(PlayerOWLData::newtGrades),
            Codec.INT.optionalFieldOf("newtSittings", 0)
                    .forGetter(PlayerOWLData::newtSittings),
            Codec.LONG.optionalFieldOf("newtLastSatTick", 0L)
                    .forGetter(PlayerOWLData::newtLastSatTick)
    ).apply(instance, (g, t, sittings, last, newt, newtSittings, newtLast) -> new PlayerOWLData(
            frozen(g), t, t ? Math.max(1, sittings) : sittings, last, frozen(newt), newtSittings, newtLast)));

    public static final PlayerOWLData DEFAULT = new PlayerOWLData(Map.of(), false, 0, 0L);

    /** An O.W.L.-only record (no N.E.W.T. sat). */
    public PlayerOWLData(@NonNull Map<OWLSubject, OWLGrade> grades, boolean examTaken, int sittings, long lastSatTick) {
        this(grades, examTaken, sittings, lastSatTick, Map.of(), 0, 0L);
    }

    private static Map<OWLSubject, OWLGrade> frozen(Map<OWLSubject, OWLGrade> grades) {
        return Collections.unmodifiableMap(grades.isEmpty() ? Map.of() : new EnumMap<>(grades));
    }

    /** The best grades held at {@code level}. */
    public Map<OWLSubject, OWLGrade> gradesAt(Qualification level) {
        return level == Qualification.OWL ? grades : newtGrades;
    }

    /** Sittings at {@code level}. */
    public int sittingsAt(Qualification level) {
        return level == Qualification.OWL ? sittings : newtSittings;
    }

    /** Game time of the last sitting at {@code level}; 0 when never. */
    public long lastSatAt(Qualification level) {
        return level == Qualification.OWL ? lastSatTick : newtLastSatTick;
    }

    /** The record after one more O.W.L. sitting at {@code tick}, with these (already merged) grades. */
    public PlayerOWLData afterSitting(Map<OWLSubject, OWLGrade> merged, long tick) {
        return afterSitting(Qualification.OWL, merged, tick);
    }

    /** The record after one more sitting at {@code level}, with these (already merged) grades. */
    public PlayerOWLData afterSitting(Qualification level, Map<OWLSubject, OWLGrade> merged, long tick) {
        return level == Qualification.OWL
                ? new PlayerOWLData(merged, true, sittings + 1, tick, newtGrades, newtSittings, newtLastSatTick)
                : new PlayerOWLData(grades, examTaken, sittings, lastSatTick, merged, newtSittings + 1, tick);
    }

    /** The same record with its O.W.L. grades replaced (admin). */
    public PlayerOWLData withGrades(Map<OWLSubject, OWLGrade> newGrades) {
        return new PlayerOWLData(newGrades, examTaken, sittings, lastSatTick, newtGrades, newtSittings, newtLastSatTick);
    }

    /** The same record with its N.E.W.T. grades replaced (admin). */
    public PlayerOWLData withNewtGrades(Map<OWLSubject, OWLGrade> newGrades) {
        return new PlayerOWLData(grades, examTaken, sittings, lastSatTick, newGrades, newtSittings, newtLastSatTick);
    }
}
