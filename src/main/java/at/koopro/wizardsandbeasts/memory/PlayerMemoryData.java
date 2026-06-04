package at.koopro.wizardsandbeasts.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerMemoryData {

    private static final Codec<Map<String, Long>> COOLDOWN_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG);

    public static final Codec<PlayerMemoryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MemoryEntry.CODEC.listOf().optionalFieldOf("memories", List.of()).forGetter(d -> d.memories),
            COOLDOWN_CODEC.optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns)
    ).apply(instance, PlayerMemoryData::new));

    private final List<MemoryEntry> memories;
    private final Map<String, Long> cooldowns;

    public PlayerMemoryData() { this(List.of(), Map.of()); }

    private PlayerMemoryData(List<MemoryEntry> memories, Map<String, Long> cooldowns) {
        this.memories = new ArrayList<>(memories);
        this.cooldowns = new HashMap<>(cooldowns);
    }

    public List<MemoryEntry> memories() { return memories; }
    public void addMemory(MemoryEntry entry) { memories.add(entry); }

    public boolean isOnCooldown(String sourceKey, long currentTick) {
        Long expiry = cooldowns.get(sourceKey);
        return expiry != null && currentTick < expiry;
    }
    public void setCooldown(String sourceKey, long expiryTick) { cooldowns.put(sourceKey, expiryTick); }

    public float happyIntensitySum() {
        float sum = 0f;
        for (MemoryEntry m : memories) if (m.type() == MemoryType.HAPPY) sum += m.intensity();
        return sum;
    }
}
