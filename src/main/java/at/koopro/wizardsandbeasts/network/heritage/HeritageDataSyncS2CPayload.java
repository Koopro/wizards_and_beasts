package at.koopro.wizardsandbeasts.network.heritage;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public record HeritageDataSyncS2CPayload(
        int syncVersion,
        String heritageId,
        String variantId,
        boolean locked,
        String transformationState,
        String activeFormId,
        boolean debugOverlay,
        Map<String, String> customFlags,
        int professionPoints,
        int totalProfessionPointsEarned,
        Set<String> unlockedProfessions,
        String selectedProfessionId,
        boolean openSelector) implements CustomPacketPayload {
    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<HeritageDataSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage_data_sync"));

    public static final StreamCodec<ByteBuf, HeritageDataSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HeritageDataSyncS2CPayload decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            String heritageId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            String variantId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            boolean locked = buf.readBoolean();
            String state = PacketCodecUtils.readString(buf);
            String activeFormId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            boolean debugOverlay = buf.readBoolean();
            int flagCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_CUSTOM_FLAGS, "custom-flags");
            Map<String, String> flags = new HashMap<>(Math.max(8, flagCount));
            for (int i = 0; i < flagCount; i++) {
                String key = PacketCodecUtils.readString(buf);
                String value = PacketCodecUtils.readString(buf);
                if (!key.isBlank()) {
                    flags.put(key, value);
                }
            }
            int professionPoints = PacketCodecUtils.clampNonNegative(buf.readInt());
            int totalProfessionPointsEarned = PacketCodecUtils.clampNonNegative(buf.readInt());
            int unlockedProfessionCount = PacketCodecUtils.readBoundedCount(
                    buf, PacketCodecUtils.MAX_UNLOCKED_PROFESSIONS, "unlocked-professions");
            Set<String> unlockedProfessions = new LinkedHashSet<>(Math.max(8, unlockedProfessionCount));
            for (int i = 0; i < unlockedProfessionCount; i++) {
                String professionId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
                if (!professionId.isBlank()) {
                    unlockedProfessions.add(professionId);
                }
            }
            String selectedProfessionId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            boolean open = buf.readBoolean();
            return new HeritageDataSyncS2CPayload(syncVersion, heritageId, variantId, locked, state, activeFormId, debugOverlay, flags,
                    professionPoints, totalProfessionPointsEarned, unlockedProfessions, selectedProfessionId, open);
        }

        @Override
        public void encode(ByteBuf buf, HeritageDataSyncS2CPayload pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            PacketCodecUtils.writeString(buf, pkt.heritageId == null ? "" : pkt.heritageId);
            PacketCodecUtils.writeString(buf, pkt.variantId == null ? "" : pkt.variantId);
            buf.writeBoolean(pkt.locked);
            PacketCodecUtils.writeString(buf, pkt.transformationState == null ? TransformationState.NORMAL.name() : pkt.transformationState);
            PacketCodecUtils.writeString(buf, pkt.activeFormId == null ? "" : pkt.activeFormId);
            buf.writeBoolean(pkt.debugOverlay);
            buf.writeInt(pkt.customFlags.size());
            for (Map.Entry<String, String> entry : pkt.customFlags.entrySet()) {
                PacketCodecUtils.writeString(buf, entry.getKey());
                PacketCodecUtils.writeString(buf, entry.getValue());
            }
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.professionPoints));
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.totalProfessionPointsEarned));
            buf.writeInt(pkt.unlockedProfessions.size());
            for (String professionId : pkt.unlockedProfessions) {
                PacketCodecUtils.writeString(buf, professionId);
            }
            PacketCodecUtils.writeString(buf, pkt.selectedProfessionId == null ? "" : pkt.selectedProfessionId);
            buf.writeBoolean(pkt.openSelector);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToPlayer(ServerPlayer player, boolean openSelector) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        PacketDistributor.sendToPlayer(player, new HeritageDataSyncS2CPayload(
                NEXT_SYNC_VERSION.incrementAndGet(),
                data.getSelectedHeritage() == null ? "" : data.getSelectedHeritage().getId(),
                data.getSelectedHeritageVariant() == null ? "" : data.getSelectedHeritageVariant().getId(),
                data.isLocked(),
                data.getTransformationState().name(),
                data.getActiveFormId() == null ? "" : data.getActiveFormId(),
                data.isDebugOverlay(),
                new HashMap<>(data.getCustomFlags()),
                data.getProfessionPoints(),
                data.getTotalProfessionPointsEarned(),
                new LinkedHashSet<>(data.getUnlockedProfessions()),
                data.getSelectedProfessionId() == null ? "" : data.getSelectedProfessionId(),
                openSelector));
    }
}
