package at.koopro.wizardsandbeasts.network.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.stats.ClientStatsState;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumMap;
import java.util.Map;

public record PlayerStatsSyncPayload(PlayerStatsData data) implements CustomPacketPayload {

    public static final Type<PlayerStatsSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "player_stats_sync"));

    public static final StreamCodec<ByteBuf, PlayerStatsSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerStatsSyncPayload decode(ByteBuf buf) {
            int power                  = PacketCodecUtils.clampNonNegative(buf.readInt());
            int precision              = PacketCodecUtils.clampNonNegative(buf.readInt());
            int willpower              = PacketCodecUtils.clampNonNegative(buf.readInt());
            int reflexes               = PacketCodecUtils.clampNonNegative(buf.readInt());
            boolean isProdigy          = buf.readBoolean();
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

            int knowledge = PacketCodecUtils.clampNonNegative(buf.readInt());

            PlayerStatsData decoded = new PlayerStatsData(
                    power, precision, willpower, reflexes,
                    isProdigy, powerGrowthAccumulated, training, knowledge);
            return new PlayerStatsSyncPayload(decoded);
        }

        @Override
        public void encode(ByteBuf buf, PlayerStatsSyncPayload pkt) {
            PlayerStatsData d = pkt.data();
            buf.writeInt(d.power());
            buf.writeInt(d.precision());
            buf.writeInt(d.willpower());
            buf.writeInt(d.reflexes());
            buf.writeBoolean(d.isProdigy());
            buf.writeInt(d.powerGrowthAccumulated());

            Map<PlayerStat, Float> training = d.trainingProgress();
            buf.writeInt(training.size());
            training.forEach((stat, progress) -> {
                PacketCodecUtils.writeString(buf, stat.getId());
                buf.writeFloat(progress);
            });

            buf.writeInt(d.knowledge());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(PlayerStatsSyncPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientStatsState.applySync(pkt.data()));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerStatsData data = player.getData(ModAttachments.PLAYER_STATS.get())
                .withKnowledge(PlayerStatsAPI.computeKnowledge(player));
        PacketDistributor.sendToPlayer(player, new PlayerStatsSyncPayload(data));
    }
}
