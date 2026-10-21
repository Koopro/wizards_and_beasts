package at.koopro.wizardsandbeasts.owl.career.track;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Career tab draws, decided on the server.
 *
 * <p>The client never sees a track file or a raw record: it gets this snapshot, every line already worded and
 * every number already computed. What the tab shows is therefore exactly what the server would act on, and a
 * datapack's track never has to be synced.
 *
 * @param state       {@link #NO_CAREER}, {@link #FLAT_CAREER} (a career without a track) or {@link #TRACK}
 * @param profession  the career's name; empty without one
 * @param rank        the rank held
 * @param nextRank    the next rank; empty at the top
 * @param xp          career experience
 * @param nextXp      experience the next rank asks for; -1 at the top
 * @param rankIndex   0-based
 * @param rankCount   ranks in the track
 * @param wageKnuts   today's wage at this rank
 * @param coinPaid    whether contract coin reaches the vault (Gringotts module on)
 * @param maxActive   contracts that may be held at once
 */
@NullMarked
public record CareerView(int state, Component profession, Component rank, Component nextRank, int xp, int nextXp,
                         int rankIndex, int rankCount, long wageKnuts, boolean coinPaid, int maxActive,
                         List<GoalLine> goals, List<ContractLine> active, List<ContractLine> offers) {

    public static final int NO_CAREER = 0;
    public static final int FLAT_CAREER = 1;
    public static final int TRACK = 2;

    public static final CareerView NONE = new CareerView(NO_CAREER, Component.empty(), Component.empty(),
            Component.empty(), 0, -1, 0, 0, 0, false, 0, List.of(), List.of(), List.of());

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;

    /** One milestone of the next rank. */
    public record GoalLine(Component text, int progress, int target) {
        public boolean met() {
            return progress >= target;
        }

        public static final StreamCodec<ByteBuf, GoalLine> STREAM_CODEC = StreamCodec.composite(
                TEXT, GoalLine::text,
                ByteBufCodecs.VAR_INT, GoalLine::progress,
                ByteBufCodecs.VAR_INT, GoalLine::target,
                GoalLine::new);
    }

    /**
     * A contract on the board or in hand.
     *
     * @param progress  counted activity, or for a delivery the matching items carried right now
     * @param daysLeft  in-game days left including today (in hand), or the contract's length (on offer)
     * @param ready     a delivery that can be turned in now
     */
    public record ContractLine(String id, Component title, Component brief, Component objective, int progress,
                               int target, boolean delivery, int daysLeft, long knuts, int xp, boolean ready) {

        public static final StreamCodec<ByteBuf, ContractLine> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ContractLine decode(ByteBuf buf) {
                return new ContractLine(ByteBufCodecs.STRING_UTF8.decode(buf), TEXT.decode(buf), TEXT.decode(buf),
                        TEXT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean());
            }

            @Override
            public void encode(ByteBuf buf, ContractLine line) {
                ByteBufCodecs.STRING_UTF8.encode(buf, line.id());
                TEXT.encode(buf, line.title());
                TEXT.encode(buf, line.brief());
                TEXT.encode(buf, line.objective());
                ByteBufCodecs.VAR_INT.encode(buf, line.progress());
                ByteBufCodecs.VAR_INT.encode(buf, line.target());
                buf.writeBoolean(line.delivery());
                ByteBufCodecs.VAR_INT.encode(buf, line.daysLeft());
                ByteBufCodecs.VAR_LONG.encode(buf, line.knuts());
                ByteBufCodecs.VAR_INT.encode(buf, line.xp());
                buf.writeBoolean(line.ready());
            }
        };
    }

    public static final StreamCodec<ByteBuf, CareerView> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CareerView decode(ByteBuf buf) {
            int state = buf.readByte();
            Component profession = TEXT.decode(buf);
            Component rank = TEXT.decode(buf);
            Component nextRank = TEXT.decode(buf);
            int xp = ByteBufCodecs.VAR_INT.decode(buf);
            int nextXp = buf.readInt();
            int rankIndex = ByteBufCodecs.VAR_INT.decode(buf);
            int rankCount = ByteBufCodecs.VAR_INT.decode(buf);
            long wage = ByteBufCodecs.VAR_LONG.decode(buf);
            boolean coinPaid = buf.readBoolean();
            int maxActive = ByteBufCodecs.VAR_INT.decode(buf);
            return new CareerView(state, profession, rank, nextRank, xp, nextXp, rankIndex, rankCount, wage, coinPaid,
                    maxActive, list(buf, GoalLine.STREAM_CODEC), list(buf, ContractLine.STREAM_CODEC),
                    list(buf, ContractLine.STREAM_CODEC));
        }

        @Override
        public void encode(ByteBuf buf, CareerView view) {
            buf.writeByte(view.state());
            TEXT.encode(buf, view.profession());
            TEXT.encode(buf, view.rank());
            TEXT.encode(buf, view.nextRank());
            ByteBufCodecs.VAR_INT.encode(buf, view.xp());
            buf.writeInt(view.nextXp());
            ByteBufCodecs.VAR_INT.encode(buf, view.rankIndex());
            ByteBufCodecs.VAR_INT.encode(buf, view.rankCount());
            ByteBufCodecs.VAR_LONG.encode(buf, view.wageKnuts());
            buf.writeBoolean(view.coinPaid());
            ByteBufCodecs.VAR_INT.encode(buf, view.maxActive());
            writeList(buf, view.goals(), GoalLine.STREAM_CODEC);
            writeList(buf, view.active(), ContractLine.STREAM_CODEC);
            writeList(buf, view.offers(), ContractLine.STREAM_CODEC);
        }
    };

    /** Lists are bounded: a malformed packet cannot make the client allocate without limit. */
    private static final int MAX_LINES = 64;

    private static <T> List<T> list(ByteBuf buf, StreamCodec<ByteBuf, T> codec) {
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_LINES) {
            throw new IllegalArgumentException("career view list too long: " + size);
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
