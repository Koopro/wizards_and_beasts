package at.koopro.wizardsandbeasts.network.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;

public record PlayerStatsSyncPayload(PlayerStatsData data) implements CustomPacketPayload {

    public static final Type<PlayerStatsSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "player_stats_sync"));

    public static final StreamCodec<ByteBuf, PlayerStatsSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerStatsSyncPayload decode(ByteBuf buf) {
            int valueCount = PacketCodecUtils.readBoundedCount(buf, PlayerStat.values().length, "stat-values");
            Map<PlayerStat, Integer> values = new EnumMap<>(PlayerStat.class);
            for (int i = 0; i < valueCount; i++) {
                String statId = PacketCodecUtils.readString(buf);
                int value = PacketCodecUtils.clampNonNegative(buf.readInt());
                PlayerStat stat = PlayerStat.fromId(statId);
                // An unknown id is a stat this client's build does not have. Skipped rather than
                // rejected: the bound above already caps how many pairs can arrive, so a mismatched
                // build loses a number it could not have rendered instead of dropping the connection.
                if (stat != null) {
                    values.put(stat, value);
                }
            }

            boolean isProdigy = buf.readBoolean();
            int powerGrowthAccumulated = PacketCodecUtils.clampNonNegative(buf.readInt());

            int count = PacketCodecUtils.readBoundedCount(buf, PlayerStat.values().length, "training-progress");
            Map<PlayerStat, Float> training = new EnumMap<>(PlayerStat.class);
            for (int i = 0; i < count; i++) {
                String statId = PacketCodecUtils.readString(buf);
                float progress = buf.readFloat();
                PlayerStat stat = PlayerStat.fromId(statId);
                if (stat != null && stat.isTrainable()) {
                    training.put(stat, progress);
                }
            }

            return new PlayerStatsSyncPayload(
                    new PlayerStatsData(values, isProdigy, powerGrowthAccumulated, training));
        }

        @Override
        public void encode(ByteBuf buf, PlayerStatsSyncPayload pkt) {
            PlayerStatsData d = pkt.data();

            // Length-prefixed (id, value) pairs rather than one int per stat in a fixed order. The
            // positional form meant a fifth stat was a protocol break; this one carries whatever the
            // enum holds, derived stats included — KNOWLEDGE is transport-only and rides here.
            Map<PlayerStat, Integer> values = d.values();
            buf.writeInt(values.size());
            values.forEach((stat, value) -> {
                PacketCodecUtils.writeString(buf, stat.getId());
                buf.writeInt(value);
            });

            buf.writeBoolean(d.isProdigy());
            buf.writeInt(d.powerGrowthAccumulated());

            Map<PlayerStat, Float> training = d.trainingProgress();
            buf.writeInt(training.size());
            training.forEach((stat, progress) -> {
                PacketCodecUtils.writeString(buf, stat.getId());
                buf.writeFloat(progress);
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerStatsData data = player.getData(ModAttachments.PLAYER_STATS.get())
                .withKnowledge(PlayerStatsAPI.computeKnowledge(player));
        PacketDistributor.sendToPlayer(player, new PlayerStatsSyncPayload(data));
    }
}
