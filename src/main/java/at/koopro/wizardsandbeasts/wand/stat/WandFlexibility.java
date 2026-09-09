package at.koopro.wizardsandbeasts.wand.stat;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum WandFlexibility implements StringRepresentable {
    UNYIELDING(0.7f),
    RIGID(0.85f),
    PLIANT(1.0f),
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
                // Back-compat: "solid" was the pre-canon name for this slot (not an Ollivander term).
                // Old saved wands and datapacks decode to PLIANT rather than failing.
                return PLIANT;
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

    /**
     * The name a wizard reads, translated.
     *
     * <p>{@link #getDisplayName()} title-cases the enum constant, which produces English for every
     * locale — the same mechanical transform of an internal identifier the Bestiary's category
     * labels were fixed for. These are Ollivander's five words, so they are words someone wrote.
     */
    public net.minecraft.network.chat.Component label() {
        return net.minecraft.network.chat.Component.translatable(
                "wandcraft.flexibility." + getSerializedName());
    }

    /**
     * Title-cased from the constant, in English for every locale.
     *
     * <p>Not deprecated, but not for players either. Its one caller is {@code WandGiveCommands},
     * whose feedback line is admin output — a place where a mechanical name off an enum constant is
     * the honest thing to print. Anything a wizard reads wants {@link #label()}.
     */
    public String getDisplayName() {
        String lower = getSerializedName().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
