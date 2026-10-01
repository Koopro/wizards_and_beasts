package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server: "open the Control Center for me". Answered with an {@link AdminSnapshotS2CPayload} that
 * opens the screen — or, for a non-administrator, with nothing at all.
 */
@NullMarked
public record AdminOpenRequestC2SPayload() implements CustomPacketPayload {

    public static final AdminOpenRequestC2SPayload INSTANCE = new AdminOpenRequestC2SPayload();

    public static final Type<AdminOpenRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_open_request"));

    public static final StreamCodec<ByteBuf, AdminOpenRequestC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminOpenRequestC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AdminNetworkService.openFor(player);
            }
        });
    }
}
