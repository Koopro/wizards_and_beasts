package at.koopro.wizardsandbeasts.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MemoryEntry(MemoryType type, float intensity, String source, long createdGameTime) {

    public static final Codec<MemoryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MemoryType.CODEC.fieldOf("type").forGetter(MemoryEntry::type),
            Codec.FLOAT.fieldOf("intensity").forGetter(MemoryEntry::intensity),
            Codec.STRING.fieldOf("source").forGetter(MemoryEntry::source),
            Codec.LONG.fieldOf("createdGameTime").forGetter(MemoryEntry::createdGameTime)
    ).apply(instance, MemoryEntry::new));
}
