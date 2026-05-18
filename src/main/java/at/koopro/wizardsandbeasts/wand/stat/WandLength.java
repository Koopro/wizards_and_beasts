package at.koopro.wizardsandbeasts.wand.stat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum WandLength implements StringRepresentable {
    SHORT("short", "9\""),
    MEDIUM("medium", "11\""),
    STANDARD("standard", "13\""),
    LONG("long", "15\"");

    public static final com.mojang.serialization.Codec<WandLength> CODEC =
            StringRepresentable.fromEnum(WandLength::values);
    public static final StreamCodec<ByteBuf, WandLength> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], WandLength::ordinal);

    private final String name;
    private final String displayName;

    WandLength(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }
}
