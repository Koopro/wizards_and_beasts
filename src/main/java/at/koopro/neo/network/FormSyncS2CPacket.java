package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.client.form.SizeLerpTracker;
import at.koopro.neo.data.PlayerTypeData;
import at.koopro.neo.form.FormRegistry;
import at.koopro.neo.form.PlayerForm;
import at.koopro.neo.form.RenderFlag;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.form.SizeProfile;
import at.koopro.neo.form.SizeProfileRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server → Client: syncs a player's active form, size profile, and render flags.
 * Sent to ALL players tracking the target (so other players see the form change).
 */
public record FormSyncS2CPacket(
        UUID playerUUID,
        String formId,
        float scaleX, float scaleY, float scaleZ,
        float reachBonus, float knockbackResistance, float stepHeight,
        int renderFlagMask
) implements CustomPacketPayload {

    public static final Type<FormSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "form_sync"));

    public static final StreamCodec<ByteBuf, FormSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FormSyncS2CPacket decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String formId = PacketCodecUtils.readString(buf);

            float sx = buf.readFloat();
            float sy = buf.readFloat();
            float sz = buf.readFloat();
            float reach = buf.readFloat();
            float kb = buf.readFloat();
            float step = buf.readFloat();
            int flags = buf.readInt();

            return new FormSyncS2CPacket(uuid, formId, sx, sy, sz, reach, kb, step, flags);
        }

        @Override
        public void encode(ByteBuf buf, FormSyncS2CPacket pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeString(buf, pkt.formId);

            buf.writeFloat(pkt.scaleX);
            buf.writeFloat(pkt.scaleY);
            buf.writeFloat(pkt.scaleZ);
            buf.writeFloat(pkt.reachBonus);
            buf.writeFloat(pkt.knockbackResistance);
            buf.writeFloat(pkt.stepHeight);
            buf.writeInt(pkt.renderFlagMask);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(FormSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SizeProfile profile = new SizeProfile(
                    pkt.formId, pkt.scaleX, pkt.scaleY, pkt.scaleZ,
                    pkt.reachBonus, pkt.knockbackResistance, pkt.stepHeight);
            ClientFormDataHolder.update(pkt.playerUUID, pkt.formId, profile,
                    RenderFlag.fromBitmask(pkt.renderFlagMask));
            SizeLerpTracker.onScaleChanged(pkt.playerUUID, pkt.scaleY);
        });
    }

    /**
     * Builds and sends a FormSyncS2CPacket for the given player to all tracking players + self.
     */
    public static void syncToTracking(ServerPlayer player) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        String formId = data.getActiveFormId();
        if (formId == null) formId = "human_default";

        PlayerForm form = FormRegistry.getOrDefault(formId);
        SizeProfile size = SizeProfileRegistry.getOrDefault(form.sizeProfileId());
        int flagMask = form.renderFlagBitmask();

        FormSyncS2CPacket pkt = new FormSyncS2CPacket(
                player.getUUID(), formId,
                size.scaleX(), size.scaleY(), size.scaleZ(),
                size.reachBonus(), size.knockbackResistance(), size.stepHeight(),
                flagMask);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, pkt);
    }
}
