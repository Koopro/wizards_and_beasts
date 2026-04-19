package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.entity.BroomEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BroomInputPacket(
        boolean forward,
        boolean backward,
        boolean up,
        boolean down,
        boolean boosting,
        float yaw,
        float pitch
) implements CustomPacketPayload {

    public static final Type<BroomInputPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "broom_input"));

    public static final StreamCodec<ByteBuf, BroomInputPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BroomInputPacket decode(ByteBuf buf) {
            byte flags = buf.readByte();
            float y = buf.readFloat();
            float p = buf.readFloat();
            return new BroomInputPacket(
                    (flags & 1) != 0,
                    (flags & 2) != 0,
                    (flags & 4) != 0,
                    (flags & 8) != 0,
                    (flags & 16) != 0,
                    y, p);
        }

        @Override
        public void encode(ByteBuf buf, BroomInputPacket pkt) {
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

    public static void handle(BroomInputPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null && player.getVehicle() instanceof BroomEntity broom) {
                broom.setInput(pkt.forward, pkt.backward, pkt.up, pkt.down,
                        pkt.boosting, pkt.yaw, pkt.pitch);
            }
        });
    }
}
