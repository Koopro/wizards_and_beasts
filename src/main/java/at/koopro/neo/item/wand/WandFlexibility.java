package at.koopro.neo.item.wand;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum WandFlexibility implements StringRepresentable {
    RIGID("rigid", "Rigid"),
    SLIGHTLY_YIELDING("slightly_yielding", "Slightly Yielding"),
    SUPPLE("supple", "Supple"),
    QUITE_FLEXIBLE("quite_flexible", "Quite Flexible");

    public static final com.mojang.serialization.Codec<WandFlexibility> CODEC =
            StringRepresentable.fromEnum(WandFlexibility::values);
    public static final StreamCodec<ByteBuf, WandFlexibility> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], WandFlexibility::ordinal);

    private final String name;
    private final String displayName;

    WandFlexibility(String name, String displayName) {
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
