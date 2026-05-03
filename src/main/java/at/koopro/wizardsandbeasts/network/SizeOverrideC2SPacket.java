package at.koopro.wizardsandbeasts.network;

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
 * Client → Server: debug size override from the Morphologist's Wand GUI.
 * Requires operator permission.
 */
public record SizeOverrideC2SPacket(
        UUID targetUUID, float scaleX, float scaleY, float scaleZ
) implements CustomPacketPayload {
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 5.0f;

    public static final Type<SizeOverrideC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_override"));

    public static final StreamCodec<ByteBuf, SizeOverrideC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SizeOverrideC2SPacket decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            return new SizeOverrideC2SPacket(uuid, buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SizeOverrideC2SPacket pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.targetUUID);
            buf.writeFloat(pkt.scaleX);
            buf.writeFloat(pkt.scaleY);
            buf.writeFloat(pkt.scaleZ);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SizeOverrideC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;
            if (!(sender.level() instanceof ServerLevel serverLevel)) return;
            MinecraftServer server = serverLevel.getServer();

            if (!sender.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cNo permission."), false);
                return;
            }

            ServerPlayer target = server.getPlayerList().getPlayer(pkt.targetUUID);
            if (target == null) return;
            if (!Float.isFinite(pkt.scaleX) || !Float.isFinite(pkt.scaleY) || !Float.isFinite(pkt.scaleZ)) return;

            float safeX = Math.clamp(pkt.scaleX, MIN_SCALE, MAX_SCALE);
            float safeY = Math.clamp(pkt.scaleY, MIN_SCALE, MAX_SCALE);
            float safeZ = Math.clamp(pkt.scaleZ, MIN_SCALE, MAX_SCALE);

            SizeProfile override = new SizeProfile("debug_override",
                    safeX, safeY, safeZ, 0.0f, 0.0f, 0.0f);
            SizeSystemAPI.applyProfile(target, override);
            FormSyncS2CPacket.syncToTracking(target);
        });
    }
}
