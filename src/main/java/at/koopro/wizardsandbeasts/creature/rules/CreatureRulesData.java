package at.koopro.wizardsandbeasts.creature.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/** Where a world keeps its creature rules, keyed by creature id path. Per world, like spell tuning. */
@NullMarked
public final class CreatureRulesData extends SavedData {

    public static final SavedDataType<CreatureRulesData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_creature_rules",
            CreatureRulesData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, CreatureRule.CODEC)
                            .optionalFieldOf("rules", Map.of())
                            .forGetter(data -> Map.copyOf(data.rules))
            ).apply(instance, CreatureRulesData::new)));

    private final Map<String, CreatureRule> rules = new HashMap<>();

    private CreatureRulesData() {
    }

    private CreatureRulesData(Map<String, CreatureRule> stored) {
        stored.forEach((id, rule) -> {
            if (!rule.isEmpty()) {
                rules.put(id, rule);
            }
        });
    }

    public static CreatureRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, CreatureRule> rules() {
        return Map.copyOf(rules);
    }

    public CreatureRule rule(String creatureId) {
        return rules.getOrDefault(creatureId, CreatureRule.NONE);
    }

    public void put(String creatureId, CreatureRule rule) {
        if (rule.isEmpty()) {
            rules.remove(creatureId);
        } else {
            rules.put(creatureId, rule);
        }
        setDirty();
    }
}
