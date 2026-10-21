package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the tracing screen for one challenge.
 *
 * <p>Carries the path itself (quantised to 16 bits per axis) rather than its seed: the client draws
 * exactly what the server will judge, with no second copy of the generator to drift out of step. The
 * seed stays on the server.
 *
 * @param points interleaved quantised {@code x, y} pairs
 */
@NullMarked
public record SpellTraceOpenS2CPayload(long challengeId, String spellId, int difficulty, int timeLimitTicks,
                                       int startAllowanceTicks, boolean spendsSource, int[] points)
        implements CustomPacketPayload {

    /** A path is resampled at 0.01 canvas units and capped well under this by its length band. */
    public static final int MAX_POINTS = 1024;

    public static final Type<SpellTraceOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_trace_open"));

    public static final StreamCodec<ByteBuf, SpellTraceOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTraceOpenS2CPayload decode(ByteBuf buf) {
            long id = buf.readLong();
            String spellId = PacketCodecUtils.readString(buf);
            int difficulty = buf.readUnsignedByte();
            int limit = buf.readInt();
            int allowance = buf.readInt();
            boolean spends = buf.readBoolean();
            int[] points = readPairs(buf, MAX_POINTS);
            return new SpellTraceOpenS2CPayload(id, spellId, difficulty, limit, allowance, spends, points);
        }

        @Override
        public void encode(ByteBuf buf, SpellTraceOpenS2CPayload pkt) {
            buf.writeLong(pkt.challengeId);
            PacketCodecUtils.writeString(buf, pkt.spellId);
            buf.writeByte(pkt.difficulty);
            buf.writeInt(pkt.timeLimitTicks);
            buf.writeInt(pkt.startAllowanceTicks);
            buf.writeBoolean(pkt.spendsSource);
            writePairs(buf, pkt.points, MAX_POINTS);
        }
    };

    /** Interleaved u16 pairs with a u16 pair count, capped at {@code max} pairs. */
    static int[] readPairs(ByteBuf buf, int max) {
        int pairs = buf.readUnsignedShort();
        if (pairs > max) {
            throw new IllegalArgumentException("too many trace points: " + pairs);
        }
        int[] out = new int[pairs * 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.readUnsignedShort();
        }
        return out;
    }

    static void writePairs(ByteBuf buf, int[] values, int max) {
        int pairs = Math.min(values.length / 2, max);
        buf.writeShort(pairs);
        for (int i = 0; i < pairs * 2; i++) {
            buf.writeShort(values[i]);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
