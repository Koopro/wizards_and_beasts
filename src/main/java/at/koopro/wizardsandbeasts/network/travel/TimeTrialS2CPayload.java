package at.koopro.wizardsandbeasts.network.travel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialCourse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * S→C, to the flyer only: their time trial as it stands — the course to draw, how far round they are, and how it
 * ended. Sent on every change (armed, each hoop, finish or disqualification), never per tick: the client runs its own
 * clock from {@code startedAt} against the synced game time.
 *
 * @param state       {@link #CLEARED}, {@link #ARMED}, {@link #RUNNING}, {@link #FINISHED} or {@link #ENDED}
 * @param course      the hoops (empty when cleared)
 * @param passes      hoops passed so far
 * @param startedAt   server game tick of the start gate; -1 before it
 * @param resultTicks the finishing time, or -1
 * @param bestTicks   the flyer's best on this course, or -1
 * @param reasonKey   lang key for how it ended; empty otherwise
 */
@NullMarked
public record TimeTrialS2CPayload(int state, TimeTrialCourse course, int passes, long startedAt, int resultTicks,
                                  int bestTicks, String reasonKey) implements CustomPacketPayload {

    public static final int CLEARED = 0;
    public static final int ARMED = 1;
    public static final int RUNNING = 2;
    public static final int FINISHED = 3;
    public static final int ENDED = 4;

    public static final Type<TimeTrialS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "time_trial"));

    public static final StreamCodec<ByteBuf, TimeTrialS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.state);
                TimeTrialCourse.STREAM_CODEC.encode(buf, p.course);
                ByteBufCodecs.VAR_INT.encode(buf, p.passes);
                ByteBufCodecs.VAR_LONG.encode(buf, p.startedAt);
                ByteBufCodecs.INT.encode(buf, p.resultTicks);
                ByteBufCodecs.INT.encode(buf, p.bestTicks);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.reasonKey);
            },
            buf -> new TimeTrialS2CPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    TimeTrialCourse.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    /** Nothing to draw. */
    public static TimeTrialS2CPayload cleared() {
        return new TimeTrialS2CPayload(CLEARED, new TimeTrialCourse(List.of(), 0.0, 0), 0, -1L, -1, -1, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
