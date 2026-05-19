package at.koopro.wizardsandbeasts.network.form;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
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
public record FormChangeRequestC2SPayload(UUID targetUUID, String formId) implements CustomPacketPayload {

    public static final Type<FormChangeRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "form_change_request"));

    public static final StreamCodec<ByteBuf, FormChangeRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FormChangeRequestC2SPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String formId = PacketCodecUtils.readString(buf);
            return new FormChangeRequestC2SPayload(uuid, formId);
        }

        @Override
        public void encode(ByteBuf buf, FormChangeRequestC2SPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.targetUUID);
            PacketCodecUtils.writeString(buf, pkt.formId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FormChangeRequestC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;
            String safeFormId = PacketCodecUtils.normalizeIdentifier(pkt.formId);
            if (safeFormId.isBlank()) {
                sender.displayClientMessage(Component.literal("\u00A7cInvalid form id."), true);
                return;
            }

            // Get server reference
            if (!(sender.level() instanceof ServerLevel serverLevel)) return;
            MinecraftServer server = serverLevel.getServer();

            // Require operator permission
            if (!sender.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cYou don't have permission to change forms."), true);
                return;
            }

            // Find target player
            ServerPlayer target = server.getPlayerList().getPlayer(pkt.targetUUID);
            if (target == null) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cTarget player not found."), true);
                return;
            }

            if (FormRegistry.get(safeFormId) == null) {
                sender.displayClientMessage(
                        Component.literal("\u00A7cUnknown form: " + safeFormId), true);
                return;
            }

            FormSystemAPI.setPlayerForm(target, safeFormId);
            sender.displayClientMessage(
                    Component.literal("\u00A7aSet " + target.getName().getString()
                            + " form to " + safeFormId), true);
        });
    }
}
