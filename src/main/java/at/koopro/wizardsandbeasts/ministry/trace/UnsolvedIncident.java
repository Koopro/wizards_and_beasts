package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * An incident the Department's inquiry could not put a name to, left on the Auror Office's board while the scene
 * is still readable. An Auror who takes it may examine the scene and question the witnesses; if the evidence
 * gets there, the incident is attributed and the ordinary case ladder takes over.
 *
 * @param incidentId the incident
 * @param expiresAt  game tick it leaves the board — when the residue has faded
 * @param auror      the Auror working it, if any
 * @param aurorUntil game tick that Auror's hold lapses
 */
@NullMarked
public record UnsolvedIncident(long incidentId, long expiresAt, Optional<UUID> auror, long aurorUntil) {

    public static final Codec<UnsolvedIncident> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("incident").forGetter(UnsolvedIncident::incidentId),
            Codec.LONG.fieldOf("expires_at").forGetter(UnsolvedIncident::expiresAt),
            UUIDUtil.CODEC.optionalFieldOf("auror").forGetter(UnsolvedIncident::auror),
            Codec.LONG.optionalFieldOf("auror_until", 0L).forGetter(UnsolvedIncident::aurorUntil)
    ).apply(instance, UnsolvedIncident::new));

    public UnsolvedIncident withAuror(Optional<UUID> holder, long until) {
        return new UnsolvedIncident(incidentId, expiresAt, holder, holder.isPresent() ? until : 0L);
    }

    public Optional<UUID> aurorAt(long now) {
        return auror.isPresent() && now < aurorUntil ? auror : Optional.empty();
    }
}
