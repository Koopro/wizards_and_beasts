package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceJudge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * The pointer samples of one attempt — not a verdict. The server judges them against its own copy of
 * the path ({@link SpellTraceService#submit}). An empty sample list means the player closed the screen.
 *
 * @param samples interleaved quantised {@code x, y} pairs, at most {@link TraceJudge#MAX_SAMPLES}
 * @param assist  the "Steady hand" accessibility option: widens tolerance, never skips
 */
@NullMarked
public record SpellTraceSubmitC2SPayload(long challengeId, boolean assist, int[] samples)
        implements CustomPacketPayload {

    public static final Type<SpellTraceSubmitC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_trace_submit"));

    public static final StreamCodec<ByteBuf, SpellTraceSubmitC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTraceSubmitC2SPayload decode(ByteBuf buf) {
            long id = buf.readLong();
            boolean assist = buf.readBoolean();
            int[] samples = SpellTraceOpenS2CPayload.readPairs(buf, TraceJudge.MAX_SAMPLES);
            return new SpellTraceSubmitC2SPayload(id, assist, samples);
        }

        @Override
        public void encode(ByteBuf buf, SpellTraceSubmitC2SPayload pkt) {
            buf.writeLong(pkt.challengeId);
            buf.writeBoolean(pkt.assist);
            SpellTraceOpenS2CPayload.writePairs(buf, pkt.samples, TraceJudge.MAX_SAMPLES);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellTraceSubmitC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            double[] xy = new double[pkt.samples.length];
            for (int i = 0; i < xy.length; i++) {
                xy[i] = TraceJudge.dequantise(pkt.samples[i]);
            }
            SpellTraceService.submit(player, pkt.challengeId, pkt.assist, xy);
        });
    }
}
