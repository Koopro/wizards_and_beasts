package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * The "Steady hand" accessibility setting, changed on the trace screen before a trace starts. The server keeps it
 * ({@code TraceMemory}) and judges every submission by what it holds, not by anything a submission claims.
 */
@NullMarked
public record SpellTraceAssistC2SPayload(boolean steadyHand) implements CustomPacketPayload {

    public static final Type<SpellTraceAssistC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_trace_assist"));

    public static final StreamCodec<ByteBuf, SpellTraceAssistC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SpellTraceAssistC2SPayload::steadyHand, SpellTraceAssistC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellTraceAssistC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SpellTraceService.setSteadyHand(player, pkt.steadyHand());
            }
        });
    }
}
