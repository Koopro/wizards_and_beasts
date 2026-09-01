package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.floo.FlooRegistrationService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * A filled-in registration form.
 *
 * <p>Every field here is a client claim and none of it is trusted: {@link FlooRegistrationService}
 * re-checks the block, the reach, the ownership, the address and the fee from scratch. The address is
 * length-capped on decode as well, because a stream codec that will read any length is a way to make
 * the server allocate whatever a hostile client asks for, and the validator downstream would only
 * reject it afterwards.
 */
public record FlooRegisterRequestC2SPayload(@NonNull BlockPos hearthPos,
                                            @NonNull String address,
                                            boolean isPublic) implements CustomPacketPayload {

    /**
     * Hard cap on the wire, well above {@code FlooAddress.MAX_LENGTH}.
     *
     * <p>Deliberately not equal to it. This is a denial-of-service bound, not a validation rule — the
     * real limit is applied by the validator, which can explain itself to the player. Matching them
     * exactly would mean a client one character over got a silent decode failure instead of "that
     * address is too long".
     */
    private static final int MAX_WIRE_LENGTH = 256;

    public static final Type<FlooRegisterRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_register_request_c2s"));

    public static final StreamCodec<ByteBuf, FlooRegisterRequestC2SPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                BlockPos.STREAM_CODEC.encode(buf, pkt.hearthPos);
                ByteBufCodecs.stringUtf8(MAX_WIRE_LENGTH).encode(buf, pkt.address);
                ByteBufCodecs.BOOL.encode(buf, pkt.isPublic);
            },
            buf -> new FlooRegisterRequestC2SPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_WIRE_LENGTH).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(@NonNull FlooRegisterRequestC2SPayload packet, @NonNull IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            FlooRegistrationService.Outcome outcome =
                    FlooRegistrationService.submit(player, packet.hearthPos, packet.address, packet.isPublic);
            if (outcome != FlooRegistrationService.Outcome.REGISTERED) {
                // The success case already toasts from inside the service, where the fee that was
                // actually charged is known. Only the refusals are announced here.
                PlayerFeedback.actionBar(player,
                        FlooRegistrationService.message(outcome, packet.address));
            }
        });
    }
}
