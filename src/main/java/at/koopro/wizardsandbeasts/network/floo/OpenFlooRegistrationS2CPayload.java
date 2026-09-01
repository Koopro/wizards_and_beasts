package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * Opens the Ministry registration form for the hearth at {@code hearthPos}.
 *
 * <p>{@code feeKnuts} is the price <em>this</em> player would pay, already resolved on the server —
 * creative, admin status and the Gringotts module have all been taken into account. Sent rather than
 * recomputed client-side because the client cannot see the config or the allow-list, and a form that
 * quoted one price and charged another would be worse than one that quoted none.
 *
 * <p>{@code currentAddress} is empty for a fresh hearth and carries the existing name when the form
 * is being used to rename one, so the field opens with something to edit.
 */
public record OpenFlooRegistrationS2CPayload(@NonNull BlockPos hearthPos,
                                             @NonNull String currentAddress,
                                             int feeKnuts) implements CustomPacketPayload {

    public static final Type<OpenFlooRegistrationS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_floo_registration_s2c"));

    public static final StreamCodec<ByteBuf, OpenFlooRegistrationS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                BlockPos.STREAM_CODEC.encode(buf, pkt.hearthPos);
                ByteBufCodecs.STRING_UTF8.encode(buf, pkt.currentAddress);
                ByteBufCodecs.VAR_INT.encode(buf, pkt.feeKnuts);
            },
            buf -> new OpenFlooRegistrationS2CPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(@NonNull ServerPlayer player, @NonNull BlockPos hearthPos,
                            @NonNull String currentAddress, int feeKnuts) {
        PacketDistributor.sendToPlayer(player,
                new OpenFlooRegistrationS2CPayload(hearthPos, currentAddress, feeKnuts));
    }
}
