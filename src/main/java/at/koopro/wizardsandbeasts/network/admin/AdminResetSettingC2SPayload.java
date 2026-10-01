package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server: "put this setting back to its default". Carries no value, since the default is the
 * server's to know, and runs through {@link AdminSettingService#reset}.
 */
@NullMarked
public record AdminResetSettingC2SPayload(int requestId, Identifier settingId, boolean confirmed)
        implements CustomPacketPayload {

    public static final Type<AdminResetSettingC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_reset_setting"));

    public static final StreamCodec<ByteBuf, AdminResetSettingC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminResetSettingC2SPayload decode(ByteBuf buf) {
            return new AdminResetSettingC2SPayload(
                    buf.readInt(), PacketCodecUtils.readIdentifier(buf, AdminSettingService.NO_SETTING),
                    buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, AdminResetSettingC2SPayload payload) {
            buf.writeInt(payload.requestId);
            PacketCodecUtils.writeIdentifier(buf, payload.settingId);
            buf.writeBoolean(payload.confirmed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminResetSettingC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AdminNetworkService.reset(player, payload);
            }
        });
    }
}
