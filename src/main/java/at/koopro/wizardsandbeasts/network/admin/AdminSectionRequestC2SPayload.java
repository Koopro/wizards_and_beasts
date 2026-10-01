package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server: fresh state for one section, sent when the admin switches to it. Cheaper than a full
 * refresh, and keeps a section's values current when another administrator has been busy.
 *
 * @param sectionId an {@link AdminCategory#id()}; an unknown id is ignored
 */
@NullMarked
public record AdminSectionRequestC2SPayload(String sectionId) implements CustomPacketPayload {

    public static final Type<AdminSectionRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_section_request"));

    public static final StreamCodec<ByteBuf, AdminSectionRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminSectionRequestC2SPayload decode(ByteBuf buf) {
            return new AdminSectionRequestC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, AdminSectionRequestC2SPayload payload) {
            PacketCodecUtils.writeString(buf, payload.sectionId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminSectionRequestC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AdminCategory section = AdminCategory.byId(payload.sectionId);
                if (section != null) {
                    AdminNetworkService.refreshFor(player, section);
                }
            }
        });
    }
}
