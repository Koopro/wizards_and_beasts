package at.koopro.wizardsandbeasts.module.data;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsSchema;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-world module state and settings — the authoritative source once a world is loaded.
 *
 * <p>Stored as {@link SavedData} on the overworld, matching {@code FlooNetworkManager} and every other
 * world-scoped system in the mod. Being per-world is the point: two saves on one installation can have
 * different modules switched on, and changing the config later does not reach back into a world that has
 * already been seeded.
 *
 * <p>Persisted keys are {@link ModuleIds} identifiers rather than enum ordinals, so reordering the
 * {@link Module} enum is safe. State stored for an id that no longer exists is dropped with a warning.
 */
@NullMarked
public final class ModuleStateData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SavedDataType<ModuleStateData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_module_state",
            ModuleStateData::seeded,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(ModuleIds.CODEC, ModuleState.CODEC)
                            .optionalFieldOf("states", Map.of())
                            .forGetter(d -> Map.copyOf(d.states)),
                    Codec.unboundedMap(ModuleIds.CODEC, ModuleSettingsValues.CODEC)
                            .optionalFieldOf("settings", Map.of())
                            .forGetter(d -> Map.copyOf(d.settings))
            ).apply(instance, ModuleStateData::ofCodec)));

    private final Map<Module, ModuleState> states;
    private final Map<Module, ModuleSettingsValues> settings;

    private ModuleStateData(Map<Module, ModuleState> states, Map<Module, ModuleSettingsValues> settings) {
        this.states = new EnumMap<>(Module.class);
        this.states.putAll(states);
        this.settings = new EnumMap<>(Module.class);
        this.settings.putAll(settings);
    }

    /**
     * A fresh world: seed every module from the config defaults. This is the only moment the config is
     * consulted — afterwards the world owns its own state.
     */
    private static ModuleStateData seeded() {
        Map<Module, ModuleState> seed = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            seed.put(module, ModuleDefaults.configuredDefault(module));
        }
        LOGGER.info("[Modules] Seeded a new world's module state from config defaults ({} modules)", seed.size());
        return new ModuleStateData(seed, Map.of());
    }

    /**
     * Loaded from disk. Anything the current build does not define is dropped, and any module absent from
     * the save — a module added since the world was created — falls back to its config default rather than
     * silently reading as DISABLED.
     */
    private static ModuleStateData ofCodec(Map<Module, ModuleState> storedStates,
                                           Map<Module, ModuleSettingsValues> storedSettings) {
        Map<Module, ModuleState> states = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            ModuleState stored = storedStates.get(module);
            states.put(module, stored != null ? stored : ModuleDefaults.configuredDefault(module));
        }
        Map<Module, ModuleSettingsValues> settings = new EnumMap<>(Module.class);
        storedSettings.forEach((module, values) ->
                settings.put(module, values.validated(ModuleSettingsSchema.of(module))));
        return new ModuleStateData(states, settings);
    }

    public static ModuleStateData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ── reads ──

    public ModuleState state(Module module) {
        return states.getOrDefault(module, ModuleState.DISABLED);
    }

    public Map<Module, ModuleState> allStates() {
        return new LinkedHashMap<>(states);
    }

    public ModuleSettingsValues settings(Module module) {
        return settings.getOrDefault(module, ModuleSettingsValues.EMPTY);
    }

    public Map<Module, ModuleSettingsValues> allSettings() {
        return new LinkedHashMap<>(settings);
    }

    // ── writes (only from ModuleStateService, which owns validation) ──

    public void setState(Module module, ModuleState state) {
        if (states.get(module) == state) {
            return;
        }
        states.put(module, state);
        setDirty();
    }

    public void setSettings(Module module, ModuleSettingsValues values) {
        settings.put(module, values);
        setDirty();
    }
}
