package at.koopro.wizardsandbeasts.network.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.debug.DebugClientPayloadHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Server → client: whether debug mode is on for you.
 *
 * <p>Debug mode has always been a server-side set of UUIDs, which was enough while every debug
 * output was a chat message the server sent. The floating panel is drawn by the client, and a client
 * that does not know it is in debug mode would have to ask the server whether to ask the server —
 * so the flag comes down once when it changes, and once at login.
 */
@NullMarked
public record DebugModeS2CPayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<DebugModeS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_mode"));

    public static final StreamCodec<ByteBuf, DebugModeS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DebugModeS2CPayload::enabled,
            DebugModeS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new DebugModeS2CPayload(enabled));
    }

    public static void handleClient(DebugModeS2CPayload payload,
                                    net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> DebugClientPayloadHandlers.setDebugMode(payload.enabled()));
    }
}
