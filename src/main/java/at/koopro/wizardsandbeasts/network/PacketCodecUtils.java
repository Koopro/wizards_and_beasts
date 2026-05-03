package at.koopro.wizardsandbeasts.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

    public static int readBoundedCount(ByteBuf buf, int max, String label) {
        int count = buf.readInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count + " (max=" + max + ")");
        }
        return count;
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
