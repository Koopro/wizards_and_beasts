package at.koopro.neo.network;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Shared ByteBuf encode/decode helpers for network packets.
 * Eliminates repeated UUID and String serialization boilerplate.
 */
public final class PacketCodecUtils {

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
        return buf.readCharSequence(len, StandardCharsets.UTF_8).toString();
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
}
