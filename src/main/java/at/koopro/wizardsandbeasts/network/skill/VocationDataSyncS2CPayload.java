package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.skill.vocation.PlayerVocationData;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel to {@code SkillDataSyncS2CPayload}: carries committed Vocation state so the future GUI can read it.
 * Slots encode as strings; empty string = absent. State-only — no GUI in this layer.
 */
public record VocationDataSyncS2CPayload(int syncVersion, String primary, String secondary)
        implements CustomPacketPayload {

    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<VocationDataSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vocation_data_sync"));

    public static final StreamCodec<ByteBuf, VocationDataSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VocationDataSyncS2CPayload decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            String primary = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            String secondary = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            return new VocationDataSyncS2CPayload(syncVersion, primary, secondary);
        }

        @Override
        public void encode(ByteBuf buf, VocationDataSyncS2CPayload pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            PacketCodecUtils.writeString(buf, pkt.primary);
            PacketCodecUtils.writeString(buf, pkt.secondary);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String slotString(Optional<Identifier> slot) {
        return slot.map(Identifier::toString).orElse("");
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerVocationData data = VocationHelper.getData(player);
        PacketDistributor.sendToPlayer(player, new VocationDataSyncS2CPayload(
                NEXT_SYNC_VERSION.incrementAndGet(),
                slotString(data.primary()),
                slotString(data.secondary())));
    }
}
