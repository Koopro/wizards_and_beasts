package at.koopro.wizardsandbeasts.broom.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Where a world keeps its broom overrides: stat values by broom id, and the brooms withdrawn from use. */
@NullMarked
public final class BroomRulesData extends SavedData {

    public static final SavedDataType<BroomRulesData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_broom_rules",
            BroomRulesData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.FLOAT))
                            .optionalFieldOf("stats", Map.of()).forGetter(BroomRulesData::encodedStats),
                    Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(d -> List.copyOf(d.disabled))
            ).apply(instance, BroomRulesData::new)));

    private final Map<String, Map<BroomStat, Float>> stats = new HashMap<>();
    private final Set<String> disabled = new HashSet<>();

    private BroomRulesData() {
    }

    private BroomRulesData(Map<String, Map<String, Float>> storedStats, List<String> storedDisabled) {
        storedStats.forEach((broom, values) -> {
            Map<BroomStat, Float> decoded = new EnumMap<>(BroomStat.class);
            values.forEach((key, value) -> {
                BroomStat stat = BroomStat.byId(key);
                if (stat != null && Float.isFinite(value)) {
                    decoded.put(stat, Math.max(stat.min(), Math.min(stat.max(), value)));
                }
            });
            if (!decoded.isEmpty()) {
                stats.put(broom, decoded);
            }
        });
        disabled.addAll(storedDisabled);
    }

    private Map<String, Map<String, Float>> encodedStats() {
        Map<String, Map<String, Float>> out = new HashMap<>();
        stats.forEach((broom, values) -> {
            Map<String, Float> encoded = new HashMap<>();
            values.forEach((stat, value) -> encoded.put(stat.id(), value));
            out.put(broom, encoded);
        });
        return out;
    }

    public static BroomRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, Map<BroomStat, Float>> stats() {
        return Map.copyOf(stats);
    }

    public Set<String> disabled() {
        return Set.copyOf(disabled);
    }

    public void setStat(String broom, BroomStat stat, Optional<Float> value) {
        Map<BroomStat, Float> values = new EnumMap<>(BroomStat.class);
        values.putAll(stats.getOrDefault(broom, Map.of()));
        value.ifPresentOrElse(v -> values.put(stat, v), () -> values.remove(stat));
        if (values.isEmpty()) {
            stats.remove(broom);
        } else {
            stats.put(broom, values);
        }
        setDirty();
    }

    public void setDisabled(String broom, boolean off) {
        if (off ? disabled.add(broom) : disabled.remove(broom)) {
            setDirty();
        }
    }
}
