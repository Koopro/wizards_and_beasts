package at.koopro.wizardsandbeasts.network.character;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/** Server → Client: open the Character Sheet screen on the receiving client. */
public record OpenCharacterSheetPayload() implements CustomPacketPayload {

    public static final Type<OpenCharacterSheetPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_character_sheet"));

    public static final StreamCodec<ByteBuf, OpenCharacterSheetPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(OpenCharacterSheetPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(OpenCharacterSheetPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientScreenHooksInvoker.invoke("openCharacterSheetScreen"));
    }

    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenCharacterSheetPayload());
    }
}
