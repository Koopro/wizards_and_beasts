package at.koopro.wizardsandbeasts.network.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.DebugPanelService;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server: "what am I looking at?"
 *
 * <p><b>It carries no target, deliberately.</b> The obvious design has the client raycast and name
 * the block it found, and that hands anyone with a packet editor the ability to read any block
 * entity in the world by position — chest contents, other people's vault blocks, a locked trunk —
 * from any distance, with no line of sight. There is nothing here to forge: the server does its own
 * pick from the player's own rotation and answers about whatever <em>it</em> finds.
 *
 * <p>Rate limiting and the debug-mode check both live in {@link DebugPanelService}, on the server,
 * for the same reason.
 */
@NullMarked
public record DebugInspectRequestPayload() implements CustomPacketPayload {

    public static final Type<DebugInspectRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_inspect_request"));

    public static final StreamCodec<ByteBuf, DebugInspectRequestPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(DebugInspectRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DebugInspectRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DebugPanelService.answer(player);
            }
        });
    }
}
