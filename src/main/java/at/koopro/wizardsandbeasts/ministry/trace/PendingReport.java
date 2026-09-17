package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Word of an incident on its way to the Ministry. Delivery is by absolute game tick, so a report sent before
 * a restart, or about a wizard who has since logged out, still arrives on time.
 *
 * @param incidentId       the incident it is about
 * @param channel          how it travels
 * @param deliverAt        game tick it arrives
 * @param identifiesCaster whether it carries a name
 */
@NullMarked
public record PendingReport(long incidentId, ReportChannel channel, long deliverAt, boolean identifiesCaster) {

    public static final Codec<PendingReport> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("incident").forGetter(PendingReport::incidentId),
            ReportChannel.CODEC.fieldOf("channel").forGetter(PendingReport::channel),
            Codec.LONG.fieldOf("deliver_at").forGetter(PendingReport::deliverAt),
            Codec.BOOL.fieldOf("identifies").forGetter(PendingReport::identifiesCaster)
    ).apply(instance, PendingReport::new));
}
