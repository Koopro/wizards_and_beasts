package at.koopro.wizardsandbeasts.network.form;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
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

    private static final String L = "form.wizards_and_beasts.change.";

    public static void handle(FormChangeRequestC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;
            String safeFormId = PacketCodecUtils.normalizeIdentifier(pkt.formId);
            Component refused = Component.translatable(L + "refused");
            if (safeFormId.isBlank()) {
                PlayerFeedback.refuse(sender, refused, Component.translatable(L + "unknown_form"));
                return;
            }

            // Get server reference
            if (!(sender.level() instanceof ServerLevel serverLevel)) return;
            MinecraftServer server = serverLevel.getServer();

            // Require operator permission
            if (!sender.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                PlayerFeedback.refuse(sender, refused, Component.translatable(L + "no_permission"));
                return;
            }

            // Find target player
            ServerPlayer target = server.getPlayerList().getPlayer(pkt.targetUUID);
            if (target == null) {
                PlayerFeedback.refuse(sender, refused, Component.translatable(L + "no_target"));
                return;
            }

            if (FormRegistry.get(safeFormId) == null) {
                PlayerFeedback.refuse(sender, refused, Component.translatable(L + "unknown_form"));
                return;
            }

            FormSystemAPI.setPlayerForm(target, safeFormId);
            PlayerFeedback.toast(sender, NoticeKind.SUCCESS, Component.literal(safeFormId),
                    Component.translatable(L + "ok", target.getName()));
        });
    }
}
