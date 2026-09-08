package at.koopro.wizardsandbeasts.command.debug.report;

import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

/**
 * One row of a debug report, in a form that survives the trip to a client.
 *
 * <p>Deliberately dumb: a kind, two strings, a colour and a number. Nothing here knows what a
 * {@code Component} is, because the same row has to come out as chat on the server and as glyphs in
 * a floating panel on the client, and a report that carried styled components could only do the
 * first. Rendering is the renderer's business; this is the content.
 *
 * @param kind     what the row is, which is what each renderer switches on
 * @param label    the left-hand side, or the whole text for {@link Kind#HEADER} and friends
 * @param value    the right-hand side; empty for rows that have none
 * @param rgb      the value's colour, or {@code -1} to let the renderer pick from {@code kind}
 * @param progress 0–1 for {@link Kind#BAR}; ignored otherwise
 */
@NullMarked
public record DebugLine(Kind kind, String label, String value, int rgb, float progress) {

    /** Sentinel for "no explicit colour" — the renderer's default for the kind wins. */
    public static final int DEFAULT_COLOUR = -1;

    public enum Kind {
        /** A section break inside a report: {@code contents (2/6)}. */
        SECTION,
        /** A labelled value. */
        ROW,
        /** A labelled yes/no. {@code value} is already "yes" or "no". */
        FLAG,
        /** A labelled 0–1 meter. */
        BAR,
        /** An unlabelled aside, dimmed. */
        NOTE,
        /** Something that is wrong. Loud in both renderers. */
        WARN
    }

    public static final StreamCodec<ByteBuf, DebugLine> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DebugLine decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            Kind[] kinds = Kind.values();
            Kind kind = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.ROW;
            return new DebugLine(
                    kind,
                    PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf),
                    buf.readInt(),
                    buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, DebugLine line) {
            buf.writeByte(line.kind.ordinal());
            PacketCodecUtils.writeString(buf, line.label);
            PacketCodecUtils.writeString(buf, line.value);
            buf.writeInt(line.rgb);
            buf.writeFloat(line.progress);
        }
    };
}
