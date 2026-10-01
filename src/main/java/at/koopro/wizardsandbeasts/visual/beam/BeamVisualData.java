package at.koopro.wizardsandbeasts.visual.beam;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where a world keeps its beam visuals: per-spell overrides (spell key → property → text) and the custom presets.
 * Visual only — nothing here is read by a gameplay rule. Every value is re-validated on load, so a hand-edited or
 * outdated save loses the bad entries, never the world.
 */
@NullMarked
public final class BeamVisualData extends SavedData {

    /** A stored custom preset: its display name and full property text. */
    public record StoredPreset(String name, Map<String, String> values) {
        static final Codec<StoredPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(StoredPreset::name),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("values", Map.of())
                        .forGetter(StoredPreset::values)
        ).apply(instance, StoredPreset::new));
    }

    public static final SavedDataType<BeamVisualData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_beam_visuals",
            BeamVisualData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING))
                            .optionalFieldOf("overrides", Map.of()).forGetter(d -> d.overrides),
                    Codec.unboundedMap(Codec.STRING, StoredPreset.CODEC)
                            .optionalFieldOf("presets", Map.of()).forGetter(d -> d.presets)
            ).apply(instance, BeamVisualData::new)));

    private final Map<String, Map<String, String>> overrides = new TreeMap<>();
    private final Map<String, StoredPreset> presets = new TreeMap<>();

    private BeamVisualData() {
    }

    private BeamVisualData(Map<String, Map<String, String>> storedOverrides, Map<String, StoredPreset> storedPresets) {
        storedOverrides.forEach((spell, values) -> {
            String key = BeamVisualDefaults.key(spell);
            Map<String, String> clean = BeamVisuals.sanitize(values);
            if (key != null && !clean.isEmpty()) {
                overrides.put(key, clean);
            }
        });
        storedPresets.forEach((id, preset) -> {
            if (!id.contains(":") && BeamPreset.validName(preset.name()) && presets.size() < BeamPreset.MAX_CUSTOM) {
                presets.put(id, new StoredPreset(preset.name(), BeamVisuals.sanitize(preset.values())));
            }
        });
    }

    public static BeamVisualData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Every spell's overrides, copied. */
    public Map<String, Map<String, String>> overrides() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        overrides.forEach((spell, values) -> out.put(spell, Map.copyOf(values)));
        return out;
    }

    public Map<String, String> overridesFor(String spellKey) {
        return Map.copyOf(overrides.getOrDefault(spellKey, Map.of()));
    }

    /** Stores ({@code value} non-null) or removes one override. {@code value} must already be canonical. */
    public void setOverride(String spellKey, BeamVisualProperty property, @Nullable String value) {
        Map<String, String> values = new HashMap<>(overrides.getOrDefault(spellKey, Map.of()));
        if (value == null) {
            values.remove(property.id());
        } else {
            values.put(property.id(), value);
        }
        if (values.isEmpty()) {
            overrides.remove(spellKey);
        } else {
            overrides.put(spellKey, new TreeMap<>(values));
        }
        setDirty();
    }

    public Map<String, StoredPreset> presets() {
        return Map.copyOf(presets);
    }

    public @Nullable StoredPreset preset(String id) {
        return presets.get(id);
    }

    public void putPreset(String id, StoredPreset preset) {
        presets.put(id, new StoredPreset(preset.name(), BeamVisuals.sanitize(preset.values())));
        setDirty();
    }

    public boolean removePreset(String id) {
        boolean removed = presets.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
