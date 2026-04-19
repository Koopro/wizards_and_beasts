package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.form.SizeProfile;
import at.koopro.neo.form.SizeSystemAPI;
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

    public static final Type<SizeOverrideC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "size_override"));

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

            SizeProfile override = new SizeProfile("debug_override",
                    pkt.scaleX, pkt.scaleY, pkt.scaleZ, 0.0f, 0.0f, 0.0f);
            SizeSystemAPI.applyProfile(target, override);
            FormSyncS2CPacket.syncToTracking(target);
        });
    }
}
