package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.client.gui.TypeSelectionScreen;
import at.koopro.neo.data.PlayerTypeData;
import at.koopro.neo.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TypeDataSyncS2CPacket(CompoundTag data, boolean openSelector) implements CustomPacketPayload {

    public static final Type<TypeDataSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "type_data_sync"));

    public static final StreamCodec<ByteBuf, TypeDataSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TypeDataSyncS2CPacket decode(ByteBuf buf) {
            try {
                CompoundTag tag = NbtIo.read(new io.netty.buffer.ByteBufInputStream(buf));
                boolean open = buf.readBoolean();
                return new TypeDataSyncS2CPacket(tag != null ? tag : new CompoundTag(), open);
            } catch (Exception e) {
                return new TypeDataSyncS2CPacket(new CompoundTag(), false);
            }
        }

        @Override
        public void encode(ByteBuf buf, TypeDataSyncS2CPacket pkt) {
            try {
                NbtIo.write(pkt.data, new io.netty.buffer.ByteBufOutputStream(buf));
                buf.writeBoolean(pkt.openSelector);
            } catch (Exception ignored) {}
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(TypeDataSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTypeDataHolder.load(pkt.data);
            if (pkt.openSelector) {
                Minecraft.getInstance().setScreen(new TypeSelectionScreen());
            }
        });
    }

    public static void syncToPlayer(ServerPlayer player, boolean openSelector) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        PacketDistributor.sendToPlayer(player, new TypeDataSyncS2CPacket(data.save(), openSelector));
    }
}
