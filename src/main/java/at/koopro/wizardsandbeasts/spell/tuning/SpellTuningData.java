package at.koopro.wizardsandbeasts.spell.tuning;

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
 * Where a world keeps its per-spell overrides. Per world, like module state: an administrator tuning Stupefy
 * on one server is not tuning it on every world this installation opens.
 *
 * <p>Keyed by the spell's registered id. An override for a spell a later datapack removes is kept rather than
 * dropped — it is inert while the spell is absent and applies again if the spell returns, which is what an
 * administrator who temporarily disabled a datapack would expect.
 */
@NullMarked
public final class SpellTuningData extends SavedData {

    public static final SavedDataType<SpellTuningData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_spell_tuning",
            SpellTuningData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, SpellOverride.CODEC)
                            .optionalFieldOf("overrides", Map.of())
                            .forGetter(data -> Map.copyOf(data.overrides))
            ).apply(instance, SpellTuningData::new)));

    private final Map<String, SpellOverride> overrides = new HashMap<>();

    private SpellTuningData() {
    }

    private SpellTuningData(Map<String, SpellOverride> stored) {
        stored.forEach((id, override) -> {
            if (!override.isEmpty()) {
                overrides.put(id, override);
            }
        });
    }

    public static SpellTuningData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, SpellOverride> overrides() {
        return Map.copyOf(overrides);
    }

    public SpellOverride override(String spellId) {
        return overrides.getOrDefault(spellId, SpellOverride.NONE);
    }

    public void put(String spellId, SpellOverride override) {
        if (override.isEmpty()) {
            overrides.remove(spellId);
        } else {
            overrides.put(spellId, override);
        }
        setDirty();
    }
}
