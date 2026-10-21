package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Everything the Education tab and the examination desk draw, decided on the server.
 *
 * <p>Built by {@link EducationService#view}. The client never sees the evidence, the ladders' sources or a
 * question's right answer: it gets scores, thresholds and grades already computed, so what the sheet shows is what
 * the examiner would mark.
 *
 * @param enabled      the O.W.L. module is on
 * @param owlSittings  O.W.L. sittings so far
 * @param newtSittings N.E.W.T. sittings so far
 * @param owlNext      game time the O.W.L. may next be sat; 0 when now
 * @param newtNext     game time the N.E.W.T. may next be sat; 0 when now
 * @param maySitOwl    an O.W.L. sitting may open now
 * @param maySitNewt   a N.E.W.T. sitting may open now (an O.W.L. sat, a seat earned, the resit wait over)
 * @param careerOpen   a career may be chosen now
 * @param subjects     one line per examined subject
 * @param sitting      the sitting in progress, if any
 * @param lastResults  the marks of the latest sitting
 * @param next         what to do next, worded, at most a handful
 */
@NullMarked
public record EducationView(boolean enabled, int owlSittings, int newtSittings, long owlNext, long newtNext,
                            boolean maySitOwl, boolean maySitNewt, boolean careerOpen, List<SubjectLine> subjects,
                            @Nullable SittingView sitting, List<ResultView> lastResults, List<Component> next) {

    public static final EducationView NONE = new EducationView(false, 0, 0, 0L, 0L, false, false, false,
            List.of(), null, List.of(), List.of());

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    /** Lists are bounded: a malformed packet cannot make the client allocate without limit. */
    private static final int MAX_LINES = 32;

    /**
     * One subject.
     *
     * @param score       O.W.L. coursework score now (work plus lesson credit)
     * @param owl         the O.W.L. ladder's A, E and O thresholds
     * @param projected   the O.W.L. grade the coursework earns now
     * @param owlRecord   best O.W.L. grade on record, or {@code null} when never marked
     * @param newtSeat    a N.E.W.T. seat is earned in it
     * @param newtScore   N.E.W.T. coursework score now (0 for a subject with no N.E.W.T.)
     * @param newt        the N.E.W.T. ladder; all zero for a subject with no N.E.W.T.
     * @param newtProjected the N.E.W.T. grade the coursework earns now, or {@code null} with no N.E.W.T.
     * @param newtRecord  best N.E.W.T. grade on record, or {@code null}
     * @param lessons     lessons taken in it
     * @param practical   translation key of the sitting's practical for it
     */
    public record SubjectLine(OWLSubject subject, int score, int[] owl, OWLGrade projected,
                              @Nullable OWLGrade owlRecord, boolean newtSeat, int newtScore, int[] newt,
                              @Nullable OWLGrade newtProjected, @Nullable OWLGrade newtRecord, int lessons,
                              String practical) {

        public boolean hasNewt() {
            return newt[2] > 0;
        }

        static final StreamCodec<ByteBuf, SubjectLine> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SubjectLine decode(ByteBuf buf) {
                return new SubjectLine(readSubject(buf), ByteBufCodecs.VAR_INT.decode(buf), readLadder(buf), readGrade(buf),
                        readOptGrade(buf), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf), readLadder(buf),
                        readOptGrade(buf), readOptGrade(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.stringUtf8(96).decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, SubjectLine line) {
                buf.writeByte(line.subject().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, line.score());
                writeLadder(buf, line.owl());
                buf.writeByte(line.projected().ordinal());
                writeOptGrade(buf, line.owlRecord());
                buf.writeBoolean(line.newtSeat());
                ByteBufCodecs.VAR_INT.encode(buf, line.newtScore());
                writeLadder(buf, line.newt());
                writeOptGrade(buf, line.newtProjected());
                writeOptGrade(buf, line.newtRecord());
                ByteBufCodecs.VAR_INT.encode(buf, line.lessons());
                ByteBufCodecs.stringUtf8(96).encode(buf, line.practical());
            }
        };
    }

    /**
     * The sitting in progress.
     *
     * @param deadline game time it closes
     * @param papers   each paper with its practical and whether that is done (written papers: whether answered)
     * @param questions the written paper; options only, the answers stay on the server
     * @param answered the written paper was handed in
     */
    public record SittingView(Qualification level, long deadline, List<PaperLine> papers,
                              List<QuestionLine> questions, boolean answered) {

        static final StreamCodec<ByteBuf, SittingView> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SittingView decode(ByteBuf buf) {
                Qualification level = Qualification.values()[Math.floorMod(buf.readByte(), Qualification.values().length)];
                long deadline = buf.readLong();
                List<PaperLine> papers = list(buf, b -> new PaperLine(readSubject(b), ByteBufCodecs.stringUtf8(96).decode(b),
                        b.readBoolean(), b.readBoolean()));
                List<QuestionLine> questions = list(buf, b -> new QuestionLine(readSubject(b), TEXT.decode(b),
                        list(b, TEXT::decode)));
                return new SittingView(level, deadline, papers, questions, buf.readBoolean());
            }

            @Override
            public void encode(ByteBuf buf, SittingView view) {
                buf.writeByte(view.level().ordinal());
                buf.writeLong(view.deadline());
                writeList(buf, view.papers(), (b, p) -> {
                    b.writeByte(p.subject().ordinal());
                    ByteBufCodecs.stringUtf8(96).encode(b, p.practical());
                    b.writeBoolean(p.written());
                    b.writeBoolean(p.done());
                });
                writeList(buf, view.questions(), (b, q) -> {
                    b.writeByte(q.subject().ordinal());
                    TEXT.encode(b, q.question());
                    writeList(b, q.options(), TEXT::encode);
                });
                buf.writeBoolean(view.answered());
            }
        };
    }

    /** One paper of a sitting: its practical, whether it is a written section, and whether it is done. */
    public record PaperLine(OWLSubject subject, String practical, boolean written, boolean done) {}

    /** One written question, as the candidate reads it. */
    public record QuestionLine(OWLSubject subject, Component question, List<Component> options) {}

    /** One marked paper of the latest sitting. */
    public record ResultView(Qualification level, OWLSubject subject, OWLGrade coursework, OWLGrade result,
                             boolean lifted) {}

    public static final StreamCodec<ByteBuf, EducationView> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EducationView decode(ByteBuf buf) {
            boolean enabled = buf.readBoolean();
            int owlSittings = ByteBufCodecs.VAR_INT.decode(buf);
            int newtSittings = ByteBufCodecs.VAR_INT.decode(buf);
            long owlNext = buf.readLong();
            long newtNext = buf.readLong();
            boolean maySitOwl = buf.readBoolean();
            boolean maySitNewt = buf.readBoolean();
            boolean careerOpen = buf.readBoolean();
            List<SubjectLine> subjects = list(buf, SubjectLine.STREAM_CODEC::decode);
            SittingView sitting = buf.readBoolean() ? SittingView.STREAM_CODEC.decode(buf) : null;
            List<ResultView> results = list(buf, b -> new ResultView(
                    Qualification.values()[Math.floorMod(b.readByte(), Qualification.values().length)],
                    readSubject(b), readGrade(b), readGrade(b), b.readBoolean()));
            List<Component> next = list(buf, TEXT::decode);
            return new EducationView(enabled, owlSittings, newtSittings, owlNext, newtNext, maySitOwl, maySitNewt,
                    careerOpen, subjects, sitting, results, next);
        }

        @Override
        public void encode(ByteBuf buf, EducationView view) {
            buf.writeBoolean(view.enabled());
            ByteBufCodecs.VAR_INT.encode(buf, view.owlSittings());
            ByteBufCodecs.VAR_INT.encode(buf, view.newtSittings());
            buf.writeLong(view.owlNext());
            buf.writeLong(view.newtNext());
            buf.writeBoolean(view.maySitOwl());
            buf.writeBoolean(view.maySitNewt());
            buf.writeBoolean(view.careerOpen());
            writeList(buf, view.subjects(), SubjectLine.STREAM_CODEC::encode);
            buf.writeBoolean(view.sitting() != null);
            if (view.sitting() != null) {
                SittingView.STREAM_CODEC.encode(buf, view.sitting());
            }
            writeList(buf, view.lastResults(), (b, r) -> {
                b.writeByte(r.level().ordinal());
                b.writeByte(r.subject().ordinal());
                b.writeByte(r.coursework().ordinal());
                b.writeByte(r.result().ordinal());
                b.writeBoolean(r.lifted());
            });
            writeList(buf, view.next(), TEXT::encode);
        }
    };

    // -- codec helpers -------------------------------------------------------------------------------

    private static OWLSubject readSubject(ByteBuf buf) {
        int ordinal = buf.readByte();
        OWLSubject[] all = OWLSubject.values();
        if (ordinal < 0 || ordinal >= all.length) throw new IllegalArgumentException("bad subject " + ordinal);
        return all[ordinal];
    }

    private static OWLGrade readGrade(ByteBuf buf) {
        int ordinal = buf.readByte();
        OWLGrade[] all = OWLGrade.values();
        if (ordinal < 0 || ordinal >= all.length) throw new IllegalArgumentException("bad grade " + ordinal);
        return all[ordinal];
    }

    private static @Nullable OWLGrade readOptGrade(ByteBuf buf) {
        return buf.readBoolean() ? readGrade(buf) : null;
    }

    private static void writeOptGrade(ByteBuf buf, @Nullable OWLGrade grade) {
        buf.writeBoolean(grade != null);
        if (grade != null) buf.writeByte(grade.ordinal());
    }

    private static int[] readLadder(ByteBuf buf) {
        return new int[] {ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf)};
    }

    private static void writeLadder(ByteBuf buf, int[] ladder) {
        for (int i = 0; i < 3; i++) ByteBufCodecs.VAR_INT.encode(buf, ladder.length > i ? ladder[i] : 0);
    }

    private static <T> List<T> list(ByteBuf buf, Function<ByteBuf, T> reader) {
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_LINES) {
            throw new IllegalArgumentException("education view list too long: " + size);
        }
        List<T> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(reader.apply(buf));
        return out;
    }

    private static <T> void writeList(ByteBuf buf, List<T> items, java.util.function.BiConsumer<ByteBuf, T> writer) {
        int size = Math.min(items.size(), MAX_LINES);
        ByteBufCodecs.VAR_INT.encode(buf, size);
        for (int i = 0; i < size; i++) writer.accept(buf, items.get(i));
    }
}
