package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientTypeDataState;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.TransformationState;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public record TypeDataSyncS2CPacket(
        int syncVersion,
        String typeId,
        String subtypeId,
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

    public static final Type<TypeDataSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_data_sync"));

    public static final StreamCodec<ByteBuf, TypeDataSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TypeDataSyncS2CPacket decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            String typeId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            String subtypeId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
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
            return new TypeDataSyncS2CPacket(syncVersion, typeId, subtypeId, locked, state, activeFormId, debugOverlay, flags,
                    professionPoints, totalProfessionPointsEarned, unlockedProfessions, selectedProfessionId, open);
        }

        @Override
        public void encode(ByteBuf buf, TypeDataSyncS2CPacket pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            PacketCodecUtils.writeString(buf, pkt.typeId == null ? "" : pkt.typeId);
            PacketCodecUtils.writeString(buf, pkt.subtypeId == null ? "" : pkt.subtypeId);
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

    public static void handleClient(TypeDataSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            WizType type = resolveType(pkt.typeId);
            WizSubtype subtype = resolveSubtype(pkt.subtypeId);
            TransformationState state = resolveState(pkt.transformationState);
            String activeForm = pkt.activeFormId == null || pkt.activeFormId.isBlank() ? null : pkt.activeFormId;
            String selectedProfessionId = pkt.selectedProfessionId == null || pkt.selectedProfessionId.isBlank()
                    ? null : pkt.selectedProfessionId;
            ClientTypeDataState.applySync(pkt.syncVersion, type, subtype, pkt.locked, state, activeForm, pkt.debugOverlay, pkt.customFlags,
                    pkt.professionPoints, pkt.totalProfessionPointsEarned, pkt.unlockedProfessions, selectedProfessionId);
            if (pkt.openSelector) {
                openClientScreenSafe();
            }
        });
    }

    @Nullable
    private static WizType resolveType(String typeId) {
        return typeId == null || typeId.isBlank() ? null : WizType.byId(typeId);
    }

    @Nullable
    private static WizSubtype resolveSubtype(String subtypeId) {
        return subtypeId == null || subtypeId.isBlank() ? null : WizSubtype.byId(subtypeId);
    }

    private static TransformationState resolveState(String state) {
        if (state == null || state.isBlank()) {
            return TransformationState.NORMAL;
        }
        try {
            return TransformationState.valueOf(state);
        } catch (IllegalArgumentException ignored) {
            return TransformationState.NORMAL;
        }
    }

    private static void openClientScreenSafe() {
        ClientScreenHooksInvoker.invoke("openTypeSelectionScreen");
    }

    public static void syncToPlayer(ServerPlayer player, boolean openSelector) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        PacketDistributor.sendToPlayer(player, new TypeDataSyncS2CPacket(
                NEXT_SYNC_VERSION.incrementAndGet(),
                data.getSelectedType() == null ? "" : data.getSelectedType().getId(),
                data.getSelectedSubtype() == null ? "" : data.getSelectedSubtype().getId(),
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
