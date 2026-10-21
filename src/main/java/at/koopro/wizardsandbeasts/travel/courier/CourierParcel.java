package at.koopro.wizardsandbeasts.travel.courier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * What a courier parcel carries ({@code ModDataComponents.COURIER_PARCEL}): who may deliver it, from where, to whom,
 * and by when.
 *
 * <p>Every field is checked again on the server at delivery. The parcel names its one courier ({@link #courier}) and
 * its id must be the one the courier's {@code TravelRecord} has in flight, so a copied, stolen or stashed parcel is
 * paper: only one parcel per player is ever deliverable, and only by them.
 *
 * @param id         this parcel, matched against the courier's in-flight slot
 * @param courier    the player who took it on
 * @param origin     the location that dispatched it (whose standing the delivery counts toward)
 * @param destination the location whose keeper must receive it
 * @param originPos  where it was handed over; delivery must be at least {@code CourierService.MIN_DISTANCE} away
 * @param dispatched game tick it was handed over
 * @param expressBy  game tick before which delivery is express
 * @param deadline   game tick at which it lapses
 */
@NullMarked
public record CourierParcel(UUID id, UUID courier, Identifier origin, Identifier destination, BlockPos originPos,
                            long dispatched, long expressBy, long deadline) {

    public static final Codec<CourierParcel> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CourierParcel::id),
            UUIDUtil.CODEC.fieldOf("courier").forGetter(CourierParcel::courier),
            Identifier.CODEC.fieldOf("origin").forGetter(CourierParcel::origin),
            Identifier.CODEC.fieldOf("destination").forGetter(CourierParcel::destination),
            BlockPos.CODEC.fieldOf("origin_pos").forGetter(CourierParcel::originPos),
            Codec.LONG.fieldOf("dispatched").forGetter(CourierParcel::dispatched),
            Codec.LONG.fieldOf("express_by").forGetter(CourierParcel::expressBy),
            Codec.LONG.fieldOf("deadline").forGetter(CourierParcel::deadline)
    ).apply(i, CourierParcel::new));

    public static final StreamCodec<ByteBuf, CourierParcel> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public boolean lapsed(long now) {
        return now >= deadline;
    }

    public boolean express(long now) {
        return now < expressBy;
    }
}
