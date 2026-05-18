package at.koopro.wizardsandbeasts.wand.stat;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum WandFlexibility implements StringRepresentable {
    UNYIELDING(0.7f),
    RIGID(0.85f),
    SOLID(1.0f),
    SUPPLE(1.1f),
    SPRINGY(1.2f);

    public static final Codec<WandFlexibility> CODEC = Codec.stringResolver(
            WandFlexibility::getSerializedName,
            name -> {
                for (WandFlexibility flexibility : values()) {
                    if (flexibility.getSerializedName().equals(name)) {
                        return flexibility;
                    }
                }
                return SOLID;
            });

    public static final StreamCodec<ByteBuf, WandFlexibility> STREAM_CODEC = StreamCodec.of(
            (buf, flexibility) -> buf.writeByte(flexibility.ordinal()),
            buf -> values()[buf.readUnsignedByte()]);

    private final float scalingFactor;

    WandFlexibility(float scalingFactor) {
        this.scalingFactor = scalingFactor;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        String lower = getSerializedName().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public float getScalingFactor() {
        return scalingFactor;
    }
}
