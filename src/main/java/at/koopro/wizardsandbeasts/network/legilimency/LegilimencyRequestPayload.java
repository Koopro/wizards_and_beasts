package at.koopro.wizardsandbeasts.network.legilimency;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.legilimency.LegilimencyServerLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public record LegilimencyRequestPayload(int targetEntityId) implements CustomPacketPayload {
    public static final Type<LegilimencyRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "legilimency_request"));

    public static final StreamCodec<ByteBuf, LegilimencyRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LegilimencyRequestPayload decode(ByteBuf buf) {
            return new LegilimencyRequestPayload(buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, LegilimencyRequestPayload payload) {
            buf.writeInt(payload.targetEntityId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LegilimencyRequestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer)) {
                return;
            }
            LegilimencyServerLogic.handleRequest((ServerPlayer) context.player(), payload.targetEntityId);
        });
    }
}
