package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The server's verdict on a challenge.
 *
 * @param outcome    {@code TraceOutcome} ordinal
 * @param failIndex  sample index where a shape failure happened, or -1
 * @param accuracy   0..1000 (per mille), for display
 * @param retryTicks how long before another attempt at this spell may start; 0 = now
 * @param sourceKept whether the book or lesson is still there to try again from
 * @param detail     a learning gate's refusal sentence for {@code REFUSED}, else empty
 */
@NullMarked
public record SpellTraceResultS2CPayload(long challengeId, int outcome, int failIndex, int accuracy, int retryTicks,
                                         boolean sourceKept, String detail) implements CustomPacketPayload {

    public static final Type<SpellTraceResultS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_trace_result"));

    public static final StreamCodec<ByteBuf, SpellTraceResultS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTraceResultS2CPayload decode(ByteBuf buf) {
            return new SpellTraceResultS2CPayload(buf.readLong(), buf.readUnsignedByte(), buf.readInt(),
                    buf.readUnsignedShort(), buf.readInt(), buf.readBoolean(), PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SpellTraceResultS2CPayload pkt) {
            buf.writeLong(pkt.challengeId);
            buf.writeByte(pkt.outcome);
            buf.writeInt(pkt.failIndex);
            buf.writeShort(pkt.accuracy);
            buf.writeInt(pkt.retryTicks);
            buf.writeBoolean(pkt.sourceKept);
            PacketCodecUtils.writeString(buf, pkt.detail);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * A refusal sentence for {@code detail}: the component as JSON, so a translatable sentence crosses the wire and is
     * read in the client's language. Plain text that is not JSON still reads as itself ({@link #detailText}).
     */
    public static String encodeDetail(net.minecraft.network.chat.Component text) {
        return net.minecraft.network.chat.ComponentSerialization.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, text)
                .result().map(Object::toString).orElse(text.getString());
    }

    /** {@code detail} as the sentence to show. */
    public net.minecraft.network.chat.Component detailText() {
        if (detail.isEmpty()) {
            return net.minecraft.network.chat.Component.empty();
        }
        try {
            return net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(detail))
                    .result().orElse(net.minecraft.network.chat.Component.literal(detail));
        } catch (RuntimeException notJson) {
            return net.minecraft.network.chat.Component.literal(detail);
        }
    }
}
