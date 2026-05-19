package at.koopro.wizardsandbeasts.network.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BroomInputC2SPayload(
        int sequence,
        boolean forward,
        boolean backward,
        boolean up,
        boolean down,
        boolean boosting,
        float yaw,
        float pitch
) implements CustomPacketPayload {

    public static final Type<BroomInputC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom_input"));

    public static final StreamCodec<ByteBuf, BroomInputC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BroomInputC2SPayload decode(ByteBuf buf) {
            int sequence = buf.readInt();
            byte flags = buf.readByte();
            float y = buf.readFloat();
            float p = buf.readFloat();
            return new BroomInputC2SPayload(
                    sequence,
                    (flags & 1) != 0,
                    (flags & 2) != 0,
                    (flags & 4) != 0,
                    (flags & 8) != 0,
                    (flags & 16) != 0,
                    y, p);
        }

        @Override
        public void encode(ByteBuf buf, BroomInputC2SPayload pkt) {
            buf.writeInt(pkt.sequence);
            byte flags = 0;
            if (pkt.forward)  flags |= 1;
            if (pkt.backward) flags |= 2;
            if (pkt.up)       flags |= 4;
            if (pkt.down)     flags |= 8;
            if (pkt.boosting) flags |= 16;
            buf.writeByte(flags);
            buf.writeFloat(pkt.yaw);
            buf.writeFloat(pkt.pitch);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BroomInputC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null && player.getVehicle() instanceof BroomEntity broom) {
                broom.setInputFromNetwork(
                        pkt.forward,
                        pkt.backward,
                        pkt.up,
                        pkt.down,
                        pkt.boosting,
                        pkt.yaw,
                        pkt.pitch,
                        pkt.sequence,
                        player.level().getGameTime());
                if (player instanceof ServerPlayer serverPlayer) {
                    DebugHooks.logBroomInput(serverPlayer, pkt.forward, pkt.backward, pkt.up, pkt.down, pkt.boosting);
                }
            }
        });
    }
}
