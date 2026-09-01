package at.koopro.wizardsandbeasts.network.currency;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.dragot.DragotPurse;
import at.koopro.wizardsandbeasts.currency.dragot.DragotQuotes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The rate the teller is standing behind, so the counter can show it.
 *
 * <p>Its own payload rather than three more fields on {@code GringottsOpenS2CPayload}: the vault
 * balance and the exchange rate change for different reasons and at different times — the balance
 * moves when you deposit, the quote when it expires — and folding them together would mean every
 * deposit re-sent a rate and every re-quote re-sent a balance.
 *
 * <p>{@code purse} is the Dragots the player is carrying, counted server-side. The screen needs it to
 * know whether "Sell 10" is even possible, and the client cannot be trusted to count its own money
 * for a transaction the server will honour.
 *
 * <p>The devalued share is deliberately <b>not</b> here. The client must not learn which of a player's
 * coins are bad — that is the whole counterfeit mechanic — so the wire carries a total and nothing
 * finer.
 *
 * @param rate    Galleons per Dragot, as quoted to this player right now
 * @param drift   percentage the quote sits above or below the configured base
 * @param purse   Dragots the player is carrying, sound and otherwise
 */
public record DragotQuoteS2CPayload(float rate, float drift, int purse) implements CustomPacketPayload {

    public static final Type<DragotQuoteS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dragot_quote"));

    public static final StreamCodec<ByteBuf, DragotQuoteS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DragotQuoteS2CPayload decode(ByteBuf buf) {
            return new DragotQuoteS2CPayload(buf.readFloat(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, DragotQuoteS2CPayload pkt) {
            buf.writeFloat(pkt.rate);
            buf.writeFloat(pkt.drift);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.purse);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Sends the player's standing quote, rolling one if none is live. */
    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DragotQuoteS2CPayload(
                DragotQuotes.rateFor(player),
                DragotQuotes.driftPercent(player),
                DragotPurse.total(player)));
    }
}
