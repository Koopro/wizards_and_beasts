package at.koopro.wizardsandbeasts.owl.post;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * One parcel in flight.
 *
 * <p>An owl carrying a stack from one wizard to another, modelled as a scheduled hand-off rather
 * than as a bird. The brief that asked for this said to defer the AI, and it is the right call twice
 * over: an entity flying a real path across loaded chunks would be lost the moment either end
 * unloads, and the thing a player actually wants — "my parcel arrives, reliably, after a delay" — is
 * a queue, not a pathfinder.
 *
 * <p>{@code arrivalTick} is absolute overworld game time so the wait survives a restart: a relative
 * countdown would silently pause while the server is off, and a parcel sent on Friday would arrive
 * on Monday having "flown" for four days.
 *
 * @param id            this parcel, for cancellation and for reporting
 * @param sender        who sent it; the parcel returns here if it cannot be delivered
 * @param recipient     who it is for
 * @param payload       what is being carried; never empty
 * @param arrivalTick   absolute overworld game time at which delivery is attempted
 * @param returning     true once delivery failed and the parcel is on its way back to the sender
 */
@NullMarked
public record OwlParcel(UUID id, UUID sender, UUID recipient, ItemStack payload,
                        long arrivalTick, boolean returning) {

    public static final Codec<OwlParcel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(OwlParcel::id),
            UUIDUtil.CODEC.fieldOf("sender").forGetter(OwlParcel::sender),
            UUIDUtil.CODEC.fieldOf("recipient").forGetter(OwlParcel::recipient),
            ItemStack.CODEC.fieldOf("payload").forGetter(OwlParcel::payload),
            Codec.LONG.fieldOf("arrivalTick").forGetter(OwlParcel::arrivalTick),
            Codec.BOOL.optionalFieldOf("returning", false).forGetter(OwlParcel::returning)
    ).apply(instance, OwlParcel::new));

    /** Who this parcel is currently flying to — the recipient, or the sender if it is coming back. */
    public UUID currentTarget() {
        return returning ? sender : recipient;
    }

    /** True once {@code now} has caught up with the arrival time. */
    public boolean hasArrived(long now) {
        return now >= arrivalTick;
    }

    /**
     * The same parcel, turned around and re-timed.
     *
     * <p>A returned parcel flies the same duration back rather than appearing instantly, so a full
     * inventory reads as "the owl is bringing it back" instead of "the send silently failed".
     */
    public OwlParcel turnedAround(long now, long flightTicks) {
        return new OwlParcel(id, sender, recipient, payload, now + flightTicks, true);
    }
}
