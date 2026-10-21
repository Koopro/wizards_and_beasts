package at.koopro.wizardsandbeasts.education.data;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A wizard's schooling beyond the grades themselves (those stay in {@code owl.data.PlayerOWLData}).
 *
 * @param moonPhases        bit per moon phase observed through a telescope (8 phases)
 * @param constellations    bit per {@code education.Astronomy.Constellation} charted
 * @param lastObservedNight the night index last charted; one observation a night. -1 when never
 * @param lessons           lessons taken with a keeper, per subject
 * @param lastLessonDay     the day index of the last lesson; one lesson a day. -1 when never
 * @param sitting           the sitting in progress, if any
 * @param lastResults       the marked papers of the most recent sitting, for the sheet and the desk
 */
@NullMarked
public record PlayerEducationData(int moonPhases, int constellations, long lastObservedNight,
                                  Map<OWLSubject, Integer> lessons, long lastLessonDay,
                                  Optional<ExamSitting> sitting, List<ResultLine> lastResults) {

    public static final Codec<PlayerEducationData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("moonPhases", 0).forGetter(PlayerEducationData::moonPhases),
            Codec.INT.optionalFieldOf("constellations", 0).forGetter(PlayerEducationData::constellations),
            Codec.LONG.optionalFieldOf("lastObservedNight", -1L).forGetter(PlayerEducationData::lastObservedNight),
            Codec.unboundedMap(OWLSubject.CODEC, Codec.INT).optionalFieldOf("lessons", Map.of())
                    .forGetter(PlayerEducationData::lessons),
            Codec.LONG.optionalFieldOf("lastLessonDay", -1L).forGetter(PlayerEducationData::lastLessonDay),
            ExamSitting.CODEC.optionalFieldOf("sitting").forGetter(PlayerEducationData::sitting),
            ResultLine.CODEC.listOf().optionalFieldOf("lastResults", List.of()).forGetter(PlayerEducationData::lastResults)
    ).apply(i, PlayerEducationData::new));

    public static final PlayerEducationData EMPTY =
            new PlayerEducationData(0, 0, -1L, Map.of(), -1L, Optional.empty(), List.of());

    public PlayerEducationData {
        lessons = lessons.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(lessons));
        lastResults = List.copyOf(lastResults);
    }

    public int lessonsIn(OWLSubject subject) {
        return lessons.getOrDefault(subject, 0);
    }

    public PlayerEducationData withObservation(int phaseBit, int constellationBit, long night) {
        return new PlayerEducationData(moonPhases | phaseBit, constellations | constellationBit, night, lessons,
                lastLessonDay, sitting, lastResults);
    }

    public PlayerEducationData withLesson(OWLSubject subject, long day) {
        EnumMap<OWLSubject, Integer> next = new EnumMap<>(OWLSubject.class);
        next.putAll(lessons);
        next.merge(subject, 1, Integer::sum);
        return new PlayerEducationData(moonPhases, constellations, lastObservedNight, next, day, sitting, lastResults);
    }

    public PlayerEducationData withSitting(Optional<ExamSitting> next) {
        return new PlayerEducationData(moonPhases, constellations, lastObservedNight, lessons, lastLessonDay, next,
                lastResults);
    }

    /** The sitting closed and its marks kept for display. */
    public PlayerEducationData marked(List<ResultLine> results) {
        return new PlayerEducationData(moonPhases, constellations, lastObservedNight, lessons, lastLessonDay,
                Optional.empty(), results);
    }

    /**
     * One marked paper.
     *
     * @param coursework the grade the work alone earned
     * @param result     the grade awarded in this sitting (coursework, lifted by a practical or a written section)
     * @param lifted     whether the sitting raised it
     */
    public record ResultLine(Qualification level, OWLSubject subject, OWLGrade coursework, OWLGrade result,
                             boolean lifted) {
        private static final Codec<OWLGrade> GRADE = Codec.STRING.xmap(OWLGrade::valueOf, OWLGrade::name);

        public static final Codec<ResultLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Qualification.CODEC.fieldOf("level").forGetter(ResultLine::level),
                OWLSubject.CODEC.fieldOf("subject").forGetter(ResultLine::subject),
                GRADE.fieldOf("coursework").forGetter(ResultLine::coursework),
                GRADE.fieldOf("result").forGetter(ResultLine::result),
                Codec.BOOL.optionalFieldOf("lifted", false).forGetter(ResultLine::lifted)
        ).apply(i, ResultLine::new));
    }
}
