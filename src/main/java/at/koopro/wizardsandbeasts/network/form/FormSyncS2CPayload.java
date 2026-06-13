package at.koopro.wizardsandbeasts.network.form;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Server → Client: syncs a player's active form, size profile, and render flags.
 * Sent to ALL players tracking the target (so other players see the form change).
 */
public record FormSyncS2CPayload(
        UUID playerUUID,
        String formId,
        float hitboxWidth,
        float hitboxHeight,
        float modelScale,
        float modelAspectX,
        float modelAspectZ,
        float reachBonus,
        float knockbackResistance,
        float stepHeight,
        int renderFlagMask
) implements CustomPacketPayload {

    public static final Type<FormSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "form_sync"));

    public static final StreamCodec<ByteBuf, FormSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FormSyncS2CPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String formId = PacketCodecUtils.readString(buf);

            float hw  = buf.readFloat();
            float hh  = buf.readFloat();
            float ms  = buf.readFloat();
            float ax  = buf.readFloat();
            float az  = buf.readFloat();
            float reach = buf.readFloat();
            float kb    = buf.readFloat();
            float step  = buf.readFloat();
            int flags   = buf.readInt();

            return new FormSyncS2CPayload(uuid, formId, hw, hh, ms, ax, az, reach, kb, step, flags);
        }

        @Override
        public void encode(ByteBuf buf, FormSyncS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeString(buf, pkt.formId);

            buf.writeFloat(pkt.hitboxWidth);
            buf.writeFloat(pkt.hitboxHeight);
            buf.writeFloat(pkt.modelScale);
            buf.writeFloat(pkt.modelAspectX);
            buf.writeFloat(pkt.modelAspectZ);
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

    /**
     * Builds and sends a FormSyncS2CPayload for the given player to all tracking players + self.
     */
    public static void syncToTracking(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        String formId = data.getActiveFormId();
        if (formId == null) formId = "human_default";

        PlayerForm form = FormRegistry.getOrDefault(formId);
        SizeProfile size = SizeProfileRegistry.getOrDefault(form.sizeProfileId());
        int flagMask = form.renderFlagBitmask();

        FormSyncS2CPayload pkt = new FormSyncS2CPayload(
                player.getUUID(), formId,
                size.hitboxWidth(), size.hitboxHeight(),
                size.modelScale(), size.modelAspectX(), size.modelAspectZ(),
                size.reachBonus(), size.knockbackResistance(), size.stepHeight(),
                flagMask);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, pkt);
    }
}
