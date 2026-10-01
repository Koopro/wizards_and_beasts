package at.koopro.wizardsandbeasts.heritage.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a world keeps its heritage rules. Per world, like module state and spell tuning: closing a heritage on
 * one server does not close it on every world this installation opens. Keyed by heritage id.
 */
@NullMarked
public final class HeritageRulesData extends SavedData {

    public static final SavedDataType<HeritageRulesData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_heritage_rules",
            HeritageRulesData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, HeritageRule.CODEC)
                            .optionalFieldOf("rules", Map.of())
                            .forGetter(data -> Map.copyOf(data.rules))
            ).apply(instance, HeritageRulesData::new)));

    private final Map<String, HeritageRule> rules = new HashMap<>();

    private HeritageRulesData() {
    }

    private HeritageRulesData(Map<String, HeritageRule> stored) {
        stored.forEach((id, rule) -> {
            if (!rule.isEmpty()) {
                rules.put(id, rule);
            }
        });
    }

    public static HeritageRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, HeritageRule> rules() {
        return Map.copyOf(rules);
    }

    public HeritageRule rule(String heritageId) {
        return rules.getOrDefault(heritageId, HeritageRule.NONE);
    }

    public void put(String heritageId, HeritageRule rule) {
        if (rule.isEmpty()) {
            rules.remove(heritageId);
        } else {
            rules.put(heritageId, rule);
        }
        setDirty();
    }
}
