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
 * Client → server: "please set this setting to this text".
 *
 * <p>A request, not a command. The value is the same text a {@code /wandb admin config set} argument would
 * be, and it goes through the same {@link AdminSettingService#change}: authorisation, lookup, scope, parse,
 * bounds and rules are all decided again on the server. Whatever the outcome, the sender gets an
 * {@link AdminSettingResultS2CPayload} echoing {@link #requestId} with the authoritative value.
 *
 * @param requestId client-chosen correlation id, echoed back; never interpreted by the server
 * @param confirmed the administrator accepted the danger warning for this change. A crafted packet can set it,
 *                  but only deliberately: the server never applies a dangerous change without it
 */
@NullMarked
public record AdminChangeSettingC2SPayload(int requestId, Identifier settingId, String value, boolean confirmed)
        implements CustomPacketPayload {

    public static final Type<AdminChangeSettingC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_change_setting"));

    public static final StreamCodec<ByteBuf, AdminChangeSettingC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminChangeSettingC2SPayload decode(ByteBuf buf) {
            return new AdminChangeSettingC2SPayload(
                    buf.readInt(),
                    PacketCodecUtils.readIdentifier(buf, AdminSettingService.NO_SETTING),
                    PacketCodecUtils.readString(buf),
                    buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, AdminChangeSettingC2SPayload payload) {
            buf.writeInt(payload.requestId);
            PacketCodecUtils.writeIdentifier(buf, payload.settingId);
            PacketCodecUtils.writeString(buf, payload.value);
            buf.writeBoolean(payload.confirmed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminChangeSettingC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AdminNetworkService.change(player, payload);
            }
        });
    }
}
