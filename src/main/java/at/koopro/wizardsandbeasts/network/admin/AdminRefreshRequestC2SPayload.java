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

/** Client → server: resend the whole panel state (the Refresh button). Does not reopen the screen. */
@NullMarked
public record AdminRefreshRequestC2SPayload() implements CustomPacketPayload {

    public static final AdminRefreshRequestC2SPayload INSTANCE = new AdminRefreshRequestC2SPayload();

    public static final Type<AdminRefreshRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_refresh_request"));

    public static final StreamCodec<ByteBuf, AdminRefreshRequestC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminRefreshRequestC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AdminNetworkService.refreshFor(player, null);
            }
        });
    }
}
