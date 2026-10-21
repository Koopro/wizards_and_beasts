package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.market.MarketLine;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView.ContractLine;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView.GoalLine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a location's board shows, decided on the server ({@link LocationProgression#view}). Like the Career
 * tab's view, the client never sees a location file or a raw record: each line arrives worded and each number
 * computed, so the screen can only show what the server would act on. Contract and goal lines are the career's own.
 *
 * @param id        the location
 * @param name      its name
 * @param present   whether the player is there now (accepting, handing in and services need it)
 * @param tier      the standing tier held
 * @param nextTier  the next tier; empty at the top
 * @param rep       reputation earned there
 * @param nextRep   reputation the next tier asks for; -1 at the top
 * @param maxActive contracts that may be held at once
 * @param skin      the GUI skin to draw the board in; empty for plain parchment
 * @param market    the trader's goods at today's prices, when the place keeps a market
 * @param illicit   whether the place deals outside the law (its deals may reach the Ministry)
 */
@NullMarked
public record LocationView(Identifier id, Component name, String skin, boolean present, Component tier, Component nextTier,
                           int rep, int nextRep, int tierIndex, int tierCount, int maxActive, List<GoalLine> goals,
                           List<ServiceLine> services, List<ContractLine> active, List<ContractLine> offers,
                           List<MarketLine> market, boolean illicit) {

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;

    /**
     * One service.
     *
     * @param id        the service's id
     * @param unlocked  whether the player's tier allows it
     * @param available whether using it now would do anything
     * @param detail    price and effect, or why not
     */
    public record ServiceLine(Identifier id, Component name, Component detail, boolean unlocked, boolean available) {
        static final StreamCodec<ByteBuf, ServiceLine> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, ServiceLine::id,
                TEXT, ServiceLine::name,
                TEXT, ServiceLine::detail,
                ByteBufCodecs.BOOL, ServiceLine::unlocked,
                ByteBufCodecs.BOOL, ServiceLine::available,
                ServiceLine::new);
    }

    public static final StreamCodec<ByteBuf, LocationView> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LocationView decode(ByteBuf buf) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            Component name = TEXT.decode(buf);
            String skin = ByteBufCodecs.stringUtf8(32).decode(buf);
            boolean present = buf.readBoolean();
            Component tier = TEXT.decode(buf);
            Component nextTier = TEXT.decode(buf);
            int rep = ByteBufCodecs.VAR_INT.decode(buf);
            int nextRep = buf.readInt();
            int tierIndex = ByteBufCodecs.VAR_INT.decode(buf);
            int tierCount = ByteBufCodecs.VAR_INT.decode(buf);
            int maxActive = ByteBufCodecs.VAR_INT.decode(buf);
            return new LocationView(id, name, skin, present, tier, nextTier, rep, nextRep, tierIndex, tierCount, maxActive,
                    list(buf, GoalLine.STREAM_CODEC), list(buf, ServiceLine.STREAM_CODEC),
                    list(buf, ContractLine.STREAM_CODEC), list(buf, ContractLine.STREAM_CODEC),
                    list(buf, MarketLine.STREAM_CODEC), buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, LocationView view) {
            Identifier.STREAM_CODEC.encode(buf, view.id());
            TEXT.encode(buf, view.name());
            ByteBufCodecs.stringUtf8(32).encode(buf, view.skin());
            buf.writeBoolean(view.present());
            TEXT.encode(buf, view.tier());
            TEXT.encode(buf, view.nextTier());
            ByteBufCodecs.VAR_INT.encode(buf, view.rep());
            buf.writeInt(view.nextRep());
            ByteBufCodecs.VAR_INT.encode(buf, view.tierIndex());
            ByteBufCodecs.VAR_INT.encode(buf, view.tierCount());
            ByteBufCodecs.VAR_INT.encode(buf, view.maxActive());
            writeList(buf, view.goals(), GoalLine.STREAM_CODEC);
            writeList(buf, view.services(), ServiceLine.STREAM_CODEC);
            writeList(buf, view.active(), ContractLine.STREAM_CODEC);
            writeList(buf, view.offers(), ContractLine.STREAM_CODEC);
            writeList(buf, view.market(), MarketLine.STREAM_CODEC);
            buf.writeBoolean(view.illicit());
        }
    };

    /** Lists are bounded: a malformed packet cannot make the client allocate without limit. */
    private static final int MAX_LINES = 64;

    private static <T> List<T> list(ByteBuf buf, StreamCodec<ByteBuf, T> codec) {
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_LINES) {
            throw new IllegalArgumentException("location view list too long: " + size);
        }
        List<T> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(codec.decode(buf));
        }
        return out;
    }

    private static <T> void writeList(ByteBuf buf, List<T> items, StreamCodec<ByteBuf, T> codec) {
        int size = Math.min(items.size(), MAX_LINES);
        ByteBufCodecs.VAR_INT.encode(buf, size);
        for (int i = 0; i < size; i++) {
            codec.encode(buf, items.get(i));
        }
    }
}
