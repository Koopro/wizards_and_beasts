package at.koopro.wizardsandbeasts.owl.data;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record PlayerOWLData(
        @NonNull Map<OWLSubject, OWLGrade> grades,
        boolean examTaken
) {
    private static final Codec<OWLSubject> SUBJECT_CODEC = Codec.STRING.xmap(
            s -> OWLSubject.valueOf(s.toUpperCase()),
            OWLSubject::name);

    private static final Codec<OWLGrade> GRADE_CODEC = Codec.STRING.xmap(
            s -> OWLGrade.valueOf(s.toUpperCase()),
            OWLGrade::name);

    public static final Codec<PlayerOWLData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(SUBJECT_CODEC, GRADE_CODEC)
                    .optionalFieldOf("grades", Map.of())
                    .forGetter(PlayerOWLData::grades),
            Codec.BOOL.optionalFieldOf("examTaken", false)
                    .forGetter(PlayerOWLData::examTaken)
    ).apply(instance, (g, t) -> new PlayerOWLData(
            Collections.unmodifiableMap(g.isEmpty() ? Map.of() : new EnumMap<>(g)), t)));

    public static final PlayerOWLData DEFAULT = new PlayerOWLData(Map.of(), false);
}
