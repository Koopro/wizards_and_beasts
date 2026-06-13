package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record OpenFlooGuiS2CPayload(@NonNull List<FlooDestinationDto> destinations) implements CustomPacketPayload {

    public static final Type<OpenFlooGuiS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_floo_gui_s2c"));

    public static final StreamCodec<ByteBuf, OpenFlooGuiS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> FlooDestinationDto.LIST_STREAM_CODEC.encode(buf, pkt.destinations),
            buf -> new OpenFlooGuiS2CPayload(FlooDestinationDto.LIST_STREAM_CODEC.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(@NonNull ServerPlayer player, @NonNull List<FlooDestinationDto> destinations) {
        PacketDistributor.sendToPlayer(player, new OpenFlooGuiS2CPayload(destinations));
    }
}
