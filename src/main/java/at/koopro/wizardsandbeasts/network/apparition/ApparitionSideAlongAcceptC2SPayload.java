package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.sidealong.SideAlongService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server "yes, take me with you".
 *
 * <p>Carries nothing: the offer being answered is whichever one this player is currently sitting on, which
 * the server already knows and the client cannot invent. Registered now so a prompt UI can send it without
 * touching the mechanics; {@code /wandb apparate accept} does the same thing from chat until one exists.
 */
@NullMarked
public record ApparitionSideAlongAcceptC2SPayload() implements CustomPacketPayload {

    public static final ApparitionSideAlongAcceptC2SPayload INSTANCE = new ApparitionSideAlongAcceptC2SPayload();

    public static final Type<ApparitionSideAlongAcceptC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_sidealong_accept"));

    public static final StreamCodec<ByteBuf, ApparitionSideAlongAcceptC2SPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionSideAlongAcceptC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SideAlongService.accept(player);
            }
        });
    }
}
