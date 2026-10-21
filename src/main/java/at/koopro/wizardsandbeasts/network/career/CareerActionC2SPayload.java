package at.koopro.wizardsandbeasts.network.career;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * C→S: a button on the Career tab.
 *
 * <p>Carries intent only — "take this contract", "hand this in" — never a result. The server re-derives
 * today's board before accepting, and counts and takes delivery items itself, so a client cannot accept a
 * contract it was not offered or claim items it does not carry.
 */
@NullMarked
public record CareerActionC2SPayload(Action action, String contractId) implements CustomPacketPayload {

    public enum Action { VIEW, ACCEPT, ABANDON, TURN_IN }

    public static final Type<CareerActionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "career_action"));

    /** Contract ids are short; anything longer is not one, and is refused at decode. */
    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
    /** Ticks between handled actions per player: a held mouse button is not a stream of contracts. */
    private static final long MIN_TICKS_BETWEEN = 4;

    private static final PlayerScopedState<Long> LAST_ACTION = PlayerScopedState.create("career_action");

    public static final StreamCodec<ByteBuf, CareerActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CareerActionC2SPayload decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            Action[] actions = Action.values();
            Action action = ordinal >= 0 && ordinal < actions.length ? actions[ordinal] : Action.VIEW;
            return new CareerActionC2SPayload(action, ID.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, CareerActionC2SPayload payload) {
            buf.writeByte(payload.action().ordinal());
            ID.encode(buf, payload.contractId());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CareerActionC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            Long last = LAST_ACTION.get(player);
            if (last != null && now - last < MIN_TICKS_BETWEEN && now >= last) {
                return;
            }
            LAST_ACTION.put(player, now);
            switch (payload.action()) {
                case VIEW -> CareerProgression.sync(player);
                case ACCEPT -> CareerProgression.accept(player, payload.contractId());
                case ABANDON -> CareerProgression.abandon(player, payload.contractId());
                case TURN_IN -> CareerProgression.turnIn(player, payload.contractId());
            }
        });
    }
}
