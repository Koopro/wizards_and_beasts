package at.koopro.wizardsandbeasts.network.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.util.TickBudget;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * @param argument a contract id, a service id for {@link Action#USE_SERVICE}, a topic for {@link Action#TOPIC}, or
 *                 {@code good|count} for {@link Action#SELL}, {@link Action#BUY} and {@link Action#BUY_DRAGOTS}
 */
@NullMarked
public record LocationActionC2SPayload(Action action, Identifier location, String argument)
        implements CustomPacketPayload {

    public enum Action { OPEN, REFRESH, ACCEPT, ABANDON, TURN_IN, USE_SERVICE, SELL, BUY, BUY_DRAGOTS, TOPIC }

    public static final Type<LocationActionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "location_action"));

    private static final StreamCodec<ByteBuf, String> ARG = ByteBufCodecs.stringUtf8(128);
    /**
     * Board actions handled per player per server tick. A gap between actions dropped every click but the first that a
     * lagging server ran in one tick, silently; see {@code VaultActionC2SPayload#ACTIONS_PER_TICK}. A purchase rebuilds
     * and sends boards, so the budget is smaller than the vault's.
     */
    public static final int ACTIONS_PER_TICK = 16;

    private static final TickBudget BUDGET = new TickBudget("location_action", ACTIONS_PER_TICK);

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
            if (context.player() instanceof ServerPlayer player) {
                submit(player, payload);
            }
        });
    }

    /**
     * One button press as it reaches the server: handled now, or refused with a word when the tick's budget is spent.
     * Public for the tests, which send bursts the way a lagging server receives them.
     *
     * @return whether it was handled
     */
    public static boolean submit(ServerPlayer player, LocationActionC2SPayload payload) {
        TickBudget.Verdict verdict = BUDGET.spend(player, player.level().getGameTime());
        if (!verdict.taken()) {
            if (verdict == TickBudget.Verdict.REFUSED_FIRST) {
                PlayerFeedback.actionBar(player, Component.translatable("location.wizards_and_beasts.busy", ACTIONS_PER_TICK)
                        .withStyle(ChatFormatting.RED));
            }
            return false;
        }
        perform(player, payload);
        return true;
    }

    private static void perform(ServerPlayer player, LocationActionC2SPayload payload) {
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
            case TOPIC -> at.koopro.wizardsandbeasts.settlement.npc.NpcTalk.ask(player, location, payload.argument());
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
    }
}
