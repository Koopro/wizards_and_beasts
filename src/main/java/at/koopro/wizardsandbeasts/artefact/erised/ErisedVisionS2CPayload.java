package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * What one gaze into the Mirror of Erised showed, for the screen to draw. The server has decided everything; the
 * client only shows it.
 *
 * @param vision    the vision, or empty when the gazer sees only their own reflection
 * @param clue      the clue that came with it, if any
 * @param gazesLeft gazes still to come today
 * @param pulled    whether this gaze left the Mirror's pull on the gazer
 */
@NullMarked
public record ErisedVisionS2CPayload(Optional<Component> vision, Optional<Component> clue, int gazesLeft,
                                     boolean pulled) implements CustomPacketPayload {

    public static final Type<ErisedVisionS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "erised_vision"));

    private static final StreamCodec<ByteBuf, Optional<Component>> TEXT =
            ByteBufCodecs.optional(ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC);

    public static final StreamCodec<ByteBuf, ErisedVisionS2CPayload> STREAM_CODEC = StreamCodec.composite(
            TEXT, ErisedVisionS2CPayload::vision,
            TEXT, ErisedVisionS2CPayload::clue,
            ByteBufCodecs.VAR_INT.map(n -> Math.max(0, Math.min(n, ErisedSelector.GAZES_PER_DAY)), n -> n),
            ErisedVisionS2CPayload::gazesLeft,
            ByteBufCodecs.BOOL, ErisedVisionS2CPayload::pulled,
            ErisedVisionS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
