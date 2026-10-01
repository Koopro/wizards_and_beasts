package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the admin panel's state for one viewer — session info plus the descriptors of every
 * setting, or of one section.
 *
 * <p>Sent only in answer to an authorised request (open, section, refresh), never on a timer. After it, the
 * client is kept current by {@link AdminSettingResultS2CPayload} deltas.
 *
 * @param openScreen true when this answers "open the Control Center"
 * @param section    the section these descriptors cover, or null for all of them
 */
@NullMarked
public record AdminSnapshotS2CPayload(boolean openScreen,
                                      @Nullable AdminCategory section,
                                      AdminSessionInfo info,
                                      List<AdminSettingDescriptor> settings) implements CustomPacketPayload {

    private static final int MAX_SETTINGS = 512;

    public static final Type<AdminSnapshotS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_snapshot"));

    public static final StreamCodec<ByteBuf, AdminSnapshotS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminSnapshotS2CPayload decode(ByteBuf buf) {
            boolean open = buf.readBoolean();
            String sectionId = PacketCodecUtils.readString(buf);
            AdminSessionInfo info = AdminSessionInfo.read(buf);
            int count = PacketCodecUtils.readBoundedCount(buf, MAX_SETTINGS, "admin-settings");
            List<AdminSettingDescriptor> settings = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                settings.add(AdminSettingDescriptor.read(buf));
            }
            return new AdminSnapshotS2CPayload(open, sectionId.isEmpty() ? null : AdminCategory.byId(sectionId),
                    info, List.copyOf(settings));
        }

        @Override
        public void encode(ByteBuf buf, AdminSnapshotS2CPayload payload) {
            buf.writeBoolean(payload.openScreen);
            PacketCodecUtils.writeString(buf, payload.section == null ? "" : payload.section.id());
            AdminSessionInfo.write(buf, payload.info);
            int count = Math.min(payload.settings.size(), MAX_SETTINGS);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                AdminSettingDescriptor.write(buf, payload.settings.get(i));
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(AdminSnapshotS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AdminClientHandlers.onSnapshot(payload));
    }
}
