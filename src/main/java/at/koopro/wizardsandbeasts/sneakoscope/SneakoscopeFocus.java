package at.koopro.wizardsandbeasts.sneakoscope;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Focused mode as it is stored on the stack: whether the top is being held still and stared at,
 * and — while it is — which of {@link SneakoscopeTuning#BEARING_SECTORS} sectors the nearest
 * suspicious thing lies in.
 *
 * <p>The bearing lives here rather than in its own component because it is meaningless outside
 * focused mode, and a component that is only sometimes valid is a component that eventually gets
 * read when it isn't.
 *
 * @param active  focused mode is on
 * @param bearing sector index, or {@link SneakoscopeTuning#NO_BEARING} when there is nothing to
 *                point at
 */
public record SneakoscopeFocus(boolean active, int bearing) {

    public static final SneakoscopeFocus IDLE = new SneakoscopeFocus(false, SneakoscopeTuning.NO_BEARING);

    public static final Codec<SneakoscopeFocus> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(SneakoscopeFocus::active),
            Codec.INT.optionalFieldOf("bearing", SneakoscopeTuning.NO_BEARING)
                    .forGetter(SneakoscopeFocus::bearing)
    ).apply(inst, SneakoscopeFocus::new));

    public static final StreamCodec<ByteBuf, SneakoscopeFocus> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SneakoscopeFocus decode(ByteBuf buf) {
            return new SneakoscopeFocus(buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, SneakoscopeFocus value) {
            buf.writeBoolean(value.active());
            ByteBufCodecs.VAR_INT.encode(buf, value.bearing());
        }
    };

    public SneakoscopeFocus {
        if (!SneakoscopeTuning.hasBearing(bearing)) {
            bearing = SneakoscopeTuning.NO_BEARING;
        }
    }

    /** True when focused mode is on <em>and</em> there is a direction worth drawing. */
    public boolean hasArrow() {
        return active && SneakoscopeTuning.hasBearing(bearing);
    }

    public SneakoscopeFocus withBearing(int sector) {
        return new SneakoscopeFocus(active, sector);
    }
}
