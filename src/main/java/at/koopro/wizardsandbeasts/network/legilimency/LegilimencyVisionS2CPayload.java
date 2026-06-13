package at.koopro.wizardsandbeasts.network.legilimency;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record LegilimencyVisionS2CPayload(BlockPos markerPos, int durationTicks) implements CustomPacketPayload {
    public static final Type<LegilimencyVisionS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "legilimency_vision"));

    public static final StreamCodec<ByteBuf, LegilimencyVisionS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LegilimencyVisionS2CPayload decode(ByteBuf buf) {
            return new LegilimencyVisionS2CPayload(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()), buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, LegilimencyVisionS2CPayload payload) {
            buf.writeInt(payload.markerPos.getX());
            buf.writeInt(payload.markerPos.getY());
            buf.writeInt(payload.markerPos.getZ());
            buf.writeInt(payload.durationTicks);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, BlockPos pos, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new LegilimencyVisionS2CPayload(pos, durationTicks));
    }
}
