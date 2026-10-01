package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Server → client: the outcome of one change, carrying the authoritative value.
 *
 * <p>Two audiences. The requester gets it with its own {@link #requestId}, answering a pending edit —
 * applied, unchanged or rejected, the row takes {@code result.value()} as the truth. Every other
 * administrator online gets applied changes with {@link #BROADCAST}, so a panel open on their screen does
 * not keep showing a value someone else just replaced.
 */
@NullMarked
public record AdminSettingResultS2CPayload(int requestId, AdminResult result) implements CustomPacketPayload {

    /** Request id for a change this client did not ask for. */
    public static final int BROADCAST = -1;
    /** Request id for one result of a batch this client asked for (a spell or category reset). */
    public static final int BATCH = -2;

    public static final Type<AdminSettingResultS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_setting_result"));

    public static final StreamCodec<ByteBuf, AdminSettingResultS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminSettingResultS2CPayload decode(ByteBuf buf) {
            int requestId = buf.readInt();
            Identifier id = PacketCodecUtils.readIdentifier(buf, AdminSettingService.NO_SETTING);
            AdminResult.Status status = statusByName(PacketCodecUtils.readString(buf));
            AdminRejection rejection = AdminRejection.byName(PacketCodecUtils.readString(buf));
            String detail = PacketCodecUtils.readString(buf);
            String previous = PacketCodecUtils.readString(buf);
            String value = PacketCodecUtils.readString(buf);
            boolean restart = buf.readBoolean();
            return new AdminSettingResultS2CPayload(requestId, new AdminResult(id, status, rejection,
                    detail.isEmpty() ? null : detail, previous, value, restart));
        }

        @Override
        public void encode(ByteBuf buf, AdminSettingResultS2CPayload payload) {
            AdminResult result = payload.result;
            buf.writeInt(payload.requestId);
            PacketCodecUtils.writeIdentifier(buf, result.settingId());
            PacketCodecUtils.writeString(buf, result.status().name());
            PacketCodecUtils.writeString(buf, result.rejection() == null ? "" : result.rejection().name());
            PacketCodecUtils.writeString(buf, result.detailKey() == null ? "" : result.detailKey());
            PacketCodecUtils.writeString(buf, result.previousValue());
            PacketCodecUtils.writeString(buf, result.value());
            buf.writeBoolean(result.restartRequired());
        }
    };

    /** Unknown status text reads as REJECTED: a client must never mistake garbage for success. */
    private static AdminResult.Status statusByName(String name) {
        for (AdminResult.Status status : AdminResult.Status.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return AdminResult.Status.REJECTED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(AdminSettingResultS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AdminClientHandlers.onResult(payload));
    }
}
