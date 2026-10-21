package at.koopro.wizardsandbeasts.location;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a player stands at one place, for the Character Sheet's Reputation tab — the slim cousin of
 * {@link LocationView}. Built on the server ({@link LocationProgression#standings}); the client gets the tier
 * already named and the numbers already decided, never a track file or a raw record.
 *
 * <p>A board ({@code LocationView}) only reaches the client while it is open at the host, so without this the sheet
 * could not say "Trusted at Gringotts" anywhere else.
 *
 * @param tierIndex 0-based; 0 with {@code rep == 0} means the place has never dealt with the player
 * @param nextRep   reputation the next tier asks for; -1 at the top
 * @param illicit   the place deals outside the law (its standing is not something to boast of at the Ministry)
 */
@NullMarked
public record PlaceStanding(Identifier id, Component name, Component tier, Component nextTier, int rep, int nextRep,
                            int tierIndex, int tierCount, boolean illicit) {

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    /** Bounded: a malformed packet cannot make the client allocate without limit. */
    public static final int MAX_PLACES = 64;

    /** Whether the player has ever earned anything here. */
    public boolean dealtWith() {
        return rep > 0 || tierIndex > 0;
    }

    public boolean atTop() {
        return nextRep < 0;
    }

    public static final StreamCodec<ByteBuf, PlaceStanding> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlaceStanding decode(ByteBuf buf) {
            return new PlaceStanding(Identifier.STREAM_CODEC.decode(buf), TEXT.decode(buf), TEXT.decode(buf),
                    TEXT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readInt(), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, PlaceStanding place) {
            Identifier.STREAM_CODEC.encode(buf, place.id());
            TEXT.encode(buf, place.name());
            TEXT.encode(buf, place.tier());
            TEXT.encode(buf, place.nextTier());
            ByteBufCodecs.VAR_INT.encode(buf, place.rep());
            buf.writeInt(place.nextRep());
            ByteBufCodecs.VAR_INT.encode(buf, place.tierIndex());
            ByteBufCodecs.VAR_INT.encode(buf, place.tierCount());
            buf.writeBoolean(place.illicit());
        }
    };

    public static final StreamCodec<ByteBuf, List<PlaceStanding>> LIST_CODEC = new StreamCodec<>() {
        @Override
        public List<PlaceStanding> decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            if (size < 0 || size > MAX_PLACES) {
                throw new IllegalArgumentException("place standings list too long: " + size);
            }
            List<PlaceStanding> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(STREAM_CODEC.decode(buf));
            }
            return out;
        }

        @Override
        public void encode(ByteBuf buf, List<PlaceStanding> places) {
            int size = Math.min(places.size(), MAX_PLACES);
            ByteBufCodecs.VAR_INT.encode(buf, size);
            for (int i = 0; i < size; i++) {
                STREAM_CODEC.encode(buf, places.get(i));
            }
        }
    };
}
