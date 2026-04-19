package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.form.FormRegistry;
import at.koopro.neo.form.FormSystemAPI;
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
 * Client → Server: request to change a target player's form (from debug GUI).
 * Requires operator permission.
 */
public record FormChangeRequestC2SPacket(UUID targetUUID, String formId) implements CustomPacketPayload {

    public static final Type<FormChangeRequestC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "form_change_request"));

    public static final StreamCodec<ByteBuf, FormChangeRequestC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FormChangeRequestC2SPacket decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String formId = PacketCodecUtils.readString(buf);
            return new FormChangeRequestC2SPacket(uuid, formId);
        }

        @Override
        public void encode(ByteBuf buf, FormChangeRequestC2SPacket pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.targetUUID);
            PacketCodecUtils.writeString(buf, pkt.formId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FormChangeRequestC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;

            // Get server reference
            if (!(sender.level() instanceof ServerLevel serverLevel)) return;
            MinecraftServer server = serverLevel.getServer();

            // Require operator permission
            if (!sender.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cYou don't have permission to change forms."), false);
                return;
            }

            // Find target player
            ServerPlayer target = server.getPlayerList().getPlayer(pkt.targetUUID);
            if (target == null) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cTarget player not found."), false);
                return;
            }

            if (FormRegistry.get(pkt.formId) == null) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cUnknown form: " + pkt.formId), false);
                return;
            }

            FormSystemAPI.setPlayerForm(target, pkt.formId);
            sender.displayClientMessage(
                    Component.literal("\u00A7aSet " + target.getName().getString()
                            + " form to " + pkt.formId), false);
        });
    }
}
