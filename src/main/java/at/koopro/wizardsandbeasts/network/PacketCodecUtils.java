package at.koopro.wizardsandbeasts.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Shared ByteBuf encode/decode helpers for network packets.
 * Eliminates repeated UUID and String serialization boilerplate.
 */
public final class PacketCodecUtils {

    public static final int MAX_STRING_BYTES = 2048;
    public static final int MAX_KNOWN_SPELLS = 512;
    public static final int MAX_COOLDOWNS = 512;
    public static final int MAX_CAST_COUNTS = 512;
    public static final int MAX_MAP_ENTRIES = 512;
    /** Biomes one Marauder's Map may have charted. A signed short indexes the palette. */
    public static final int MAX_MAP_PALETTE = Short.MAX_VALUE;
    /** Markers on one map: discoveries plus every trusted player's pins. */
    public static final int MAX_MAP_MARKERS = 1024;
    /**
     * Bytes in one run-length-encoded map region. The uncompressed region is 3KB and the encoder
     * only ever shrinks it, so anything larger did not come from the encoder.
     */
    public static final int MAX_MAP_REGION_BYTES = 4096;
    public static final int MAX_UNLOCKED_SKILLS = 512;
    public static final int MAX_CUSTOM_FLAGS = 256;
    public static final int MAX_UNLOCKED_PROFESSIONS = 256;
    private static final int MAX_IDENTIFIER_LEN = 128;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_./:-]+");

    private PacketCodecUtils() {}

    public static UUID readUUID(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeUUID(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static String readString(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Invalid string length: " + len);
        }
        return buf.readCharSequence(len, StandardCharsets.UTF_8).toString();
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String too large for packet: " + bytes.length);
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static String normalizeIdentifier(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > MAX_IDENTIFIER_LEN) {
            return "";
        }
        return IDENTIFIER_PATTERN.matcher(trimmed).matches() ? trimmed : "";
    }

    /**
     * Reads an {@link Identifier}, falling back to {@code fallback} for anything malformed.
     *
     * <p>Every payload that carried an id used to inline the same normalise-parse-null-check dance,
     * and each copy chose its own fallback. Decoding must never return null for a field the record
     * declares non-null, and it must never throw on a hostile packet either.
     */
    public static Identifier readIdentifier(ByteBuf buf, Identifier fallback) {
        Identifier parsed = Identifier.tryParse(normalizeIdentifier(readString(buf)));
        return parsed != null ? parsed : fallback;
    }

    public static void writeIdentifier(ByteBuf buf, Identifier id) {
        writeString(buf, id.toString());
    }

    /** Reads a length-prefixed byte block, refusing anything over {@code max}. */
    public static byte[] readBytes(ByteBuf buf, int max, String label) {
        int len = buf.readInt();
        if (len < 0 || len > max) {
            throw new IllegalArgumentException("Invalid " + label + " length: " + len + " (max=" + max + ")");
        }
        byte[] out = new byte[len];
        buf.readBytes(out);
        return out;
    }

    public static void writeBytes(ByteBuf buf, byte[] data, int max, String label) {
        if (data.length > max) {
            throw new IllegalArgumentException(label + " too large for packet: " + data.length);
        }
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static int readBoundedCount(ByteBuf buf, int max, String label) {
        int count = buf.readInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count + " (max=" + max + ")");
        }
        return count;
    }

    /**
     * Writes at most {@code max} items behind an int count. Over-long lists are cut, not refused:
     * {@link #readList} throws on a count above the same {@code max}, so the two must agree on it.
     */
    public static <T> void writeList(ByteBuf buf, List<T> list, int max, BiConsumer<ByteBuf, T> writer) {
        int count = Math.min(list.size(), max);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    /** Reads a list written by {@link #writeList}; a count outside {@code [0, max]} fails the decode. */
    public static <T> List<T> readList(ByteBuf buf, int max, String what, Function<ByteBuf, T> reader) {
        int count = readBoundedCount(buf, max, what);
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }

    /** Reads an enum written as its ordinal byte; an unknown ordinal fails the decode. */
    public static <E extends Enum<E>> E readEnum(ByteBuf buf, E[] values, String what) {
        int ordinal = buf.readByte();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("unknown " + what + " " + ordinal);
        }
        return values[ordinal];
    }

    public static int clampNonNegative(int value) {
        return Math.max(0, value);
    }

    public static <T extends CustomPacketPayload> StreamCodec<ByteBuf, T> noPayloadCodec(Supplier<T> factory) {
        return new StreamCodec<>() {
            @Override
            public T decode(ByteBuf buf) {
                return factory.get();
            }

            @Override
            public void encode(ByteBuf buf, T packet) {
                // no payload
            }
        };
    }

    public static CurrencyAmounts readCurrencyAmounts(ByteBuf buf) {
        return new CurrencyAmounts(buf.readLong(), buf.readLong(), buf.readLong());
    }

    public static void writeCurrencyAmounts(ByteBuf buf, long knuts, long sickles, long galleons) {
        buf.writeLong(knuts);
        buf.writeLong(sickles);
        buf.writeLong(galleons);
    }

    public record CurrencyAmounts(long knuts, long sickles, long galleons) {
    }
}
