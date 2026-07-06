package at.koopro.wizardsandbeasts.wand.stat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public enum WandWood implements StringRepresentable {
    ELDER("elder", "Elder"),
    YEW("yew", "Yew"),
    HOLLY("holly", "Holly"),
    ROWAN("rowan", "Rowan");

    public static final com.mojang.serialization.Codec<WandWood> CODEC =
            StringRepresentable.fromEnum(WandWood::values);
    public static final StreamCodec<ByteBuf, WandWood> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], WandWood::ordinal);

    private static final Map<String, WandWood> BY_NAME = new HashMap<>();

    static {
        for (WandWood w : values()) BY_NAME.put(w.name, w);
    }

    private final String name;
    private final String displayName;

    WandWood(String name, String displayName) {
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

    @Nullable
    public static WandWood byName(String name) {
        return BY_NAME.get(name);
    }
}
