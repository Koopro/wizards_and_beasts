package at.koopro.wizardsandbeasts.network.career;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/** S→C: the Career tab's contents, built by the server ({@code CareerProgression#view}). */
@NullMarked
public record CareerViewS2CPayload(CareerView view) implements CustomPacketPayload {

    public static final Type<CareerViewS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "career_view"));

    public static final StreamCodec<ByteBuf, CareerViewS2CPayload> STREAM_CODEC =
            CareerView.STREAM_CODEC.map(CareerViewS2CPayload::new, CareerViewS2CPayload::view);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, CareerView view) {
        PacketDistributor.sendToPlayer(player, new CareerViewS2CPayload(view));
    }
}
