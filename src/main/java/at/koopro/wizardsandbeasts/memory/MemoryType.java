package at.koopro.wizardsandbeasts.memory;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum MemoryType implements StringRepresentable {
    HAPPY("happy"),
    PAINFUL("painful"),   // v1 ungenutzt — Raum für Dementoren/Pensieve später
    MUNDANE("mundane");

    public static final Codec<MemoryType> CODEC = StringRepresentable.fromValues(MemoryType::values);

    private final String serializedName;
    MemoryType(String serializedName) { this.serializedName = serializedName; }

    @Override
    public String getSerializedName() { return serializedName; }
}
