package at.koopro.wizardsandbeasts.network.education;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.education.EducationView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/** S→C: the Education tab's and the examination desk's contents, built by the server ({@code EducationService#view}). */
@NullMarked
public record EducationViewS2CPayload(EducationView view) implements CustomPacketPayload {

    public static final Type<EducationViewS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "education_view"));

    public static final StreamCodec<ByteBuf, EducationViewS2CPayload> STREAM_CODEC =
            EducationView.STREAM_CODEC.map(EducationViewS2CPayload::new, EducationViewS2CPayload::view);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, EducationView view) {
        PacketDistributor.sendToPlayer(player, new EducationViewS2CPayload(view));
    }
}
