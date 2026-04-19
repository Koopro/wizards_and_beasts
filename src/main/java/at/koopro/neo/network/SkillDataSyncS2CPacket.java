package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerSkillData;
import at.koopro.neo.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillDataSyncS2CPacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<SkillDataSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "skill_data_sync"));

    public static final StreamCodec<ByteBuf, SkillDataSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillDataSyncS2CPacket decode(ByteBuf buf) {
            try {
                CompoundTag tag = NbtIo.read(
                        new io.netty.buffer.ByteBufInputStream(buf));
                return new SkillDataSyncS2CPacket(tag != null ? tag : new CompoundTag());
            } catch (Exception e) {
                return new SkillDataSyncS2CPacket(new CompoundTag());
            }
        }

        @Override
        public void encode(ByteBuf buf, SkillDataSyncS2CPacket pkt) {
            try {
                NbtIo.write(pkt.data, new io.netty.buffer.ByteBufOutputStream(buf));
            } catch (Exception e) {
                try {
                    NbtIo.write(new CompoundTag(), new io.netty.buffer.ByteBufOutputStream(buf));
                } catch (Exception ignored) {}
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SkillDataSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSkillDataHolder.load(pkt.data));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        PacketDistributor.sendToPlayer(player, new SkillDataSyncS2CPacket(data.save()));
    }
}
