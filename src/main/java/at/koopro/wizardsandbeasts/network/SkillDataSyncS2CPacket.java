package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public record SkillDataSyncS2CPacket(
        int syncVersion,
        int skillPoints,
        int totalPointsEarned,
        Map<String, Integer> unlockedSkills) implements CustomPacketPayload {
    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<SkillDataSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_data_sync"));

    public static final StreamCodec<ByteBuf, SkillDataSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillDataSyncS2CPacket decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            int points = PacketCodecUtils.clampNonNegative(buf.readInt());
            int total = PacketCodecUtils.clampNonNegative(buf.readInt());
            int count = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_UNLOCKED_SKILLS, "unlocked-skills");
            Map<String, Integer> skills = new HashMap<>(Math.max(8, count));
            for (int i = 0; i < count; i++) {
                String id = PacketCodecUtils.readString(buf);
                int level = PacketCodecUtils.clampNonNegative(buf.readInt());
                if (!id.isBlank() && level > 0) {
                    skills.put(id, level);
                }
            }
            return new SkillDataSyncS2CPacket(syncVersion, points, total, skills);
        }

        @Override
        public void encode(ByteBuf buf, SkillDataSyncS2CPacket pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.skillPoints));
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.totalPointsEarned));
            buf.writeInt(pkt.unlockedSkills.size());
            for (Map.Entry<String, Integer> entry : pkt.unlockedSkills.entrySet()) {
                PacketCodecUtils.writeString(buf, entry.getKey());
                buf.writeInt(PacketCodecUtils.clampNonNegative(entry.getValue()));
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SkillDataSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSkillDataState.applySync(
                pkt.syncVersion, pkt.skillPoints, pkt.totalPointsEarned, pkt.unlockedSkills));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        PacketDistributor.sendToPlayer(player, new SkillDataSyncS2CPacket(
                NEXT_SYNC_VERSION.incrementAndGet(),
                data.getSkillPoints(),
                data.getTotalPointsEarned(),
                new HashMap<>(data.getUnlockedSkills())));
    }
}
