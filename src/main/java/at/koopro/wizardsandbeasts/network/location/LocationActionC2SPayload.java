package at.koopro.wizardsandbeasts.network.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.Locations;
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
 * C→S: a button on a location's board.
 *
 * <p>Intent only — "take this contract", "use this service" — never a result. The server re-derives the board,
 * re-checks the module, the player's presence and tier, and counts items itself. {@code OPEN} is the Gringotts
 * screen's "bank business" button: honoured only when the player is already there.
 *
 * @param argument a contract id, a service id for {@link Action#USE_SERVICE}, or {@code good|count} for
 *                 {@link Action#SELL}, {@link Action#BUY} and {@link Action#BUY_DRAGOTS}
 */
@NullMarked
public record LocationActionC2SPayload(Action action, Identifier location, String argument)
        implements CustomPacketPayload {

    public enum Action { OPEN, REFRESH, ACCEPT, ABANDON, TURN_IN, USE_SERVICE, SELL, BUY, BUY_DRAGOTS }

    public static final Type<LocationActionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "location_action"));

    private static final StreamCodec<ByteBuf, String> ARG = ByteBufCodecs.stringUtf8(128);
    /** Ticks between handled actions per player: a held mouse button is not a stream of purchases. */
    private static final long MIN_TICKS_BETWEEN = 4;

    private static final PlayerScopedState<Long> LAST_ACTION = PlayerScopedState.create("location_action");

    public static final StreamCodec<ByteBuf, LocationActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LocationActionC2SPayload decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            Action[] actions = Action.values();
            Action action = ordinal >= 0 && ordinal < actions.length ? actions[ordinal] : Action.REFRESH;
            return new LocationActionC2SPayload(action, Identifier.STREAM_CODEC.decode(buf), ARG.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, LocationActionC2SPayload payload) {
            buf.writeByte(payload.action().ordinal());
            Identifier.STREAM_CODEC.encode(buf, payload.location());
            ARG.encode(buf, payload.argument());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocationActionC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            if (!PlayerScopedState.throttle(LAST_ACTION, player, now, MIN_TICKS_BETWEEN)) {
                return;
            }
            Identifier location = payload.location();
            switch (payload.action()) {
                case OPEN -> {
                    LocationDefinition def = Locations.open(location);
                    if (def != null && LocationPresence.isAt(player, def)) {
                        LocationProgression.sync(player, location, true);
                    }
                }
                case REFRESH -> LocationProgression.sync(player, location, false);
                case ACCEPT -> LocationProgression.accept(player, location, payload.argument());
                case ABANDON -> LocationProgression.abandon(player, location, payload.argument());
                case TURN_IN -> LocationProgression.turnIn(player, location, payload.argument());
                case USE_SERVICE -> {
                    Identifier service = Identifier.tryParse(payload.argument());
                    if (service != null) {
                        LocationProgression.useService(player, location, service);
                    }
                }
                case SELL, BUY, BUY_DRAGOTS -> {
                    // "good|count": a malformed count is a refused deal, never a default.
                    String arg = payload.argument();
                    int bar = arg.lastIndexOf('|');
                    if (bar <= 0) {
                        return;
                    }
                    int count;
                    try {
                        count = Integer.parseInt(arg.substring(bar + 1));
                    } catch (NumberFormatException e) {
                        return;
                    }
                    String good = arg.substring(0, bar);
                    if (payload.action() == Action.SELL) {
                        at.koopro.wizardsandbeasts.market.MarketTrade.sell(player, location, good, count);
                    } else {
                        at.koopro.wizardsandbeasts.market.MarketTrade.buy(player, location, good, count,
                                payload.action() == Action.BUY_DRAGOTS
                                        ? at.koopro.wizardsandbeasts.market.MarketTrade.Tender.DRAGOTS
                                        : at.koopro.wizardsandbeasts.market.MarketTrade.Tender.VAULT);
                    }
                }
            }
        });
    }
}
