package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * "Let me trace this spell": the Try again button, and a click on a skill-web node whose lesson is
 * still owed. Names only the spell; the server finds a real source for it itself (a pending lesson, or
 * a matching book in hand) and refuses when there is none.
 */
@NullMarked
public record SpellTraceRequestC2SPayload(String spellId) implements CustomPacketPayload {

    public static final Type<SpellTraceRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_trace_request"));

    public static final StreamCodec<ByteBuf, SpellTraceRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTraceRequestC2SPayload decode(ByteBuf buf) {
            return new SpellTraceRequestC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SpellTraceRequestC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.spellId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellTraceRequestC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SpellTraceService.request(player, pkt.spellId);
            }
        });
    }
}
