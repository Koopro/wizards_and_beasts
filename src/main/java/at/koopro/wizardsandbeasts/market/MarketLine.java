package at.koopro.wizardsandbeasts.market;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

/**
 * One good on a location board's market section, worded and priced on the server ({@link MarketTrade#lines}). The
 * client draws it and sends back only the good's id and how many.
 *
 * @param good       the good's id in its list
 * @param buying     true when the trader buys it (the player sells), false when the trader sells it
 * @param name       what it is
 * @param unitKnuts  the next unit's price now, after pressure, demand and the server scale
 * @param count      for goods bought: how many the player carries; for goods sold: units left this week, -1 unlimited
 * @param available  whether a deal would do anything now
 * @param unlocked   whether the player's tier allows it
 * @param inDemand   the trader is short of it today and pays more
 * @param deal       what the law makes of it, so the board can mark restricted and illicit goods
 * @param saturation how far trade has moved its price, 0–100
 * @param note       why not, or the weekly allowance
 * @param dragots    for goods sold: Dragots asked for the next unit at this till, 0 when the keeper takes none
 */
@NullMarked
public record MarketLine(String good, boolean buying, Component name, long unitKnuts, int count, boolean available,
                         boolean unlocked, boolean inDemand, DealClass deal, int saturation, Component note,
                         int dragots) {

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;

    public static final StreamCodec<ByteBuf, MarketLine> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MarketLine decode(ByteBuf buf) {
            String good = ByteBufCodecs.stringUtf8(64).decode(buf);
            boolean buying = buf.readBoolean();
            Component name = TEXT.decode(buf);
            long unit = ByteBufCodecs.VAR_LONG.decode(buf);
            int count = buf.readInt();
            byte flags = buf.readByte();
            int ordinal = buf.readByte();
            DealClass[] classes = DealClass.values();
            DealClass deal = ordinal >= 0 && ordinal < classes.length ? classes[ordinal] : DealClass.LEGAL;
            int saturation = buf.readByte();
            Component note = TEXT.decode(buf);
            int dragots = ByteBufCodecs.VAR_INT.decode(buf);
            return new MarketLine(good, buying, name, unit, count, (flags & 1) != 0, (flags & 2) != 0,
                    (flags & 4) != 0, deal, saturation, note, dragots);
        }

        @Override
        public void encode(ByteBuf buf, MarketLine line) {
            ByteBufCodecs.stringUtf8(64).encode(buf, line.good());
            buf.writeBoolean(line.buying());
            TEXT.encode(buf, line.name());
            ByteBufCodecs.VAR_LONG.encode(buf, Math.max(0L, line.unitKnuts()));
            buf.writeInt(line.count());
            buf.writeByte((line.available() ? 1 : 0) | (line.unlocked() ? 2 : 0) | (line.inDemand() ? 4 : 0));
            buf.writeByte(line.deal().ordinal());
            buf.writeByte(Math.clamp(line.saturation(), 0, 100));
            TEXT.encode(buf, line.note());
            ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, line.dragots()));
        }
    };
}
