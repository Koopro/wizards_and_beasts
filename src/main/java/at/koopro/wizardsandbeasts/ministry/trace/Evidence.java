package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * One item of evidence on one incident, with its provenance.
 *
 * <p>{@code source} is who or what it came from — the official, the Muggle, the testifying player, the Auror who
 * examined the scene — so the same source is never counted twice and an operator can always see where a case
 * came from. {@code sourceLabel} is that source's name at the time, for files read while they are gone.
 *
 * @param kind        what it is
 * @param incidentId  the incident it bears on
 * @param source      the entity or player it came from, if any (a Trace reading has none)
 * @param sourceLabel a display name for the source, or empty
 * @param tick        game tick it was taken
 */
@NullMarked
public record Evidence(EvidenceKind kind, long incidentId, Optional<UUID> source, String sourceLabel, long tick) {

    public static final Codec<Evidence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EvidenceKind.CODEC.fieldOf("kind").forGetter(Evidence::kind),
            Codec.LONG.fieldOf("incident").forGetter(Evidence::incidentId),
            UUIDUtil.CODEC.optionalFieldOf("source").forGetter(Evidence::source),
            Codec.STRING.optionalFieldOf("source_label", "").forGetter(Evidence::sourceLabel),
            Codec.LONG.optionalFieldOf("tick", 0L).forGetter(Evidence::tick)
    ).apply(instance, Evidence::new));

    public static Evidence of(EvidenceKind kind, long incidentId, long tick) {
        return new Evidence(kind, incidentId, Optional.empty(), "", tick);
    }

    public static Evidence from(EvidenceKind kind, long incidentId, UUID source, String label, long tick) {
        return new Evidence(kind, incidentId, Optional.of(source), label, tick);
    }
}
