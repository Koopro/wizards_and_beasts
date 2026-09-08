package at.koopro.wizardsandbeasts.network.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server "flip my hood" request.
 *
 * <p>Carries nothing: which stack the toggle lands on is the server's decision
 * ({@link RobeHood#toggleTarget}), not the client's. A payload naming a slot would let a client
 * write the component onto any stack it liked.
 *
 * <p>The server writes the component; the client sees the new state when the stack syncs back, and
 * so does everyone else looking at the wearer — which is the whole reason the state is a
 * synchronized component rather than client-side.
 */
@NullMarked
public record HoodToggleC2SPayload() implements CustomPacketPayload {

    public static final Type<HoodToggleC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hood_toggle_c2s"));

    public static final StreamCodec<ByteBuf, HoodToggleC2SPayload> STREAM_CODEC =
            StreamCodec.unit(new HoodToggleC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Boolean up = RobeHood.toggle(player);

            if (up == null) {
                return; // No hooded robe worn or held — a keypress with nothing to act on is not an error.
            }

            PlayerFeedback.actionBar(player, Component.translatable(
                    up ? "message.wizards_and_beasts.hood_up" : "message.wizards_and_beasts.hood_down"));
        });
    }
}
