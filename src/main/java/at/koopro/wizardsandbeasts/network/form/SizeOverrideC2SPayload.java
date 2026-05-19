package at.koopro.wizardsandbeasts.network.form;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeSystemAPI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → Server: debug size override from the Morph Debug GUI.
 * Requires operator permission.
 */
public record SizeOverrideC2SPayload(
        UUID targetUUID,
        float hitboxHeight,
        float modelScale,
        float modelAspectX,
        float modelAspectZ
) implements CustomPacketPayload {

    private static final float MIN_HITBOX_H  = 0.1f;
    private static final float MAX_HITBOX_H  = 9.0f;
    private static final float MIN_MODEL     = 0.1f;
    private static final float MAX_MODEL     = 5.0f;
    private static final float MIN_ASPECT    = 0.1f;
    private static final float MAX_ASPECT    = 3.0f;

    public static final Type<SizeOverrideC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_override"));

    public static final StreamCodec<ByteBuf, SizeOverrideC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SizeOverrideC2SPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            return new SizeOverrideC2SPayload(uuid,
                    buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SizeOverrideC2SPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.targetUUID);
            buf.writeFloat(pkt.hitboxHeight);
            buf.writeFloat(pkt.modelScale);
            buf.writeFloat(pkt.modelAspectX);
            buf.writeFloat(pkt.modelAspectZ);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SizeOverrideC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;
            if (!(sender.level() instanceof ServerLevel serverLevel)) return;
            MinecraftServer server = serverLevel.getServer();

            if (!sender.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                sender.displayClientMessage(
                        Component.literal("§cNo permission."), false);
                return;
            }

            ServerPlayer target = server.getPlayerList().getPlayer(pkt.targetUUID);
            if (target == null) return;
            if (!Float.isFinite(pkt.hitboxHeight) || !Float.isFinite(pkt.modelScale)
                    || !Float.isFinite(pkt.modelAspectX) || !Float.isFinite(pkt.modelAspectZ)) return;

            float safeHH = Math.clamp(pkt.hitboxHeight,  MIN_HITBOX_H, MAX_HITBOX_H);
            float safeMS = Math.clamp(pkt.modelScale,    MIN_MODEL,    MAX_MODEL);
            float safeAX = Math.clamp(pkt.modelAspectX,  MIN_ASPECT,   MAX_ASPECT);
            float safeAZ = Math.clamp(pkt.modelAspectZ,  MIN_ASPECT,   MAX_ASPECT);
            float hw     = safeHH / 3.0f; // hitboxWidth derived (0.6 / 1.8 = 1/3)

            SizeProfile override = new SizeProfile("debug_override",
                    hw, safeHH, safeMS, safeAX, safeAZ,
                    0.0f, 0.0f, 0.0f);
            SizeSystemAPI.applyProfile(target, override);
            FormSyncS2CPayload.syncToTracking(target);
        });
    }
}
