package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ApparitionRequestPayload(BlockPos targetBlockPos, Vec3 targetPosition) implements CustomPacketPayload {
    public static final Type<ApparitionRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_request"));

    public static final StreamCodec<ByteBuf, ApparitionRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ApparitionRequestPayload decode(ByteBuf buf) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            double px = buf.readDouble();
            double py = buf.readDouble();
            double pz = buf.readDouble();
            return new ApparitionRequestPayload(new BlockPos(x, y, z), new Vec3(px, py, pz));
        }

        @Override
        public void encode(ByteBuf buf, ApparitionRequestPayload payload) {
            buf.writeInt(payload.targetBlockPos.getX());
            buf.writeInt(payload.targetBlockPos.getY());
            buf.writeInt(payload.targetBlockPos.getZ());
            buf.writeDouble(payload.targetPosition.x);
            buf.writeDouble(payload.targetPosition.y);
            buf.writeDouble(payload.targetPosition.z);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ApparitionServerLogic.handleRequest(player, payload.targetBlockPos, payload.targetPosition);
        });
    }
}
