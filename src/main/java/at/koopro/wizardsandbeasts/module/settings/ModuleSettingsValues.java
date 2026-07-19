package at.koopro.wizardsandbeasts.module.settings;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stored values for one module's settings.
 *
 * <p>Values are kept in their encoded form ({@link com.google.gson.JsonElement} via {@link JsonOps}) rather
 * than as {@code Object}, so the store is type-agnostic: it can round-trip and sync a value whose type only
 * the {@link SettingDefinition} knows, without a cast anywhere. Reading goes back through the definition's
 * codec, which is also where a type mismatch is caught.
 *
 * <p>Validation against the schema happens on load: unknown keys are dropped with a warning, out-of-range
 * values are clamped and the clamp is logged, and anything missing simply reads as its default.
 */
@NullMarked
public final class ModuleSettingsValues {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ModuleSettingsValues EMPTY = new ModuleSettingsValues(Map.of());

    /** Setting key → encoded value. */
    public static final Codec<ModuleSettingsValues> CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.PASSTHROUGH)
                    .xmap(ModuleSettingsValues::fromDynamics, ModuleSettingsValues::toDynamics);

    private final Map<Identifier, JsonElement> encoded;

    private ModuleSettingsValues(Map<Identifier, JsonElement> encoded) {
        this.encoded = Collections.unmodifiableMap(new LinkedHashMap<>(encoded));
    }

    private static ModuleSettingsValues fromDynamics(Map<Identifier, Dynamic<?>> raw) {
        Map<Identifier, JsonElement> map = new LinkedHashMap<>();
        raw.forEach((key, dynamic) -> map.put(key, dynamic.convert(JsonOps.INSTANCE).getValue()));
        return new ModuleSettingsValues(map);
    }

    private Map<Identifier, Dynamic<?>> toDynamics() {
        Map<Identifier, Dynamic<?>> map = new LinkedHashMap<>();
        encoded.forEach((key, json) -> map.put(key, new Dynamic<>(JsonOps.INSTANCE, json)));
        return map;
    }

    public boolean isEmpty() {
        return encoded.isEmpty();
    }

    public Set<Identifier> keys() {
        return encoded.keySet();
    }

    /** Raw encoded view — used by the sync codec, not by gameplay callers. */
    public Map<Identifier, JsonElement> raw() {
        return encoded;
    }

    /**
     * Reads a setting, falling back to its default when unset or unreadable. Never throws: a value that no
     * longer parses (a setting whose type changed between versions) logs and yields the default.
     */
    public <T> T get(SettingDefinition<T> definition) {
        JsonElement stored = encoded.get(definition.key());
        if (stored == null) {
            return definition.defaultValue();
        }
        DataResult<T> parsed = definition.valueCodec().parse(JsonOps.INSTANCE, stored);
        Optional<T> value = parsed.result();
        if (value.isEmpty()) {
            LOGGER.warn("[Modules] Unreadable value for setting {} — using default. {}",
                    definition.key(), parsed.error().map(DataResult.Error::message).orElse(""));
            return definition.defaultValue();
        }
        return definition.clamp(value.get());
    }

    /** Returns a copy with {@code definition} set to {@code value}, clamped to the definition's bounds. */
    public <T> ModuleSettingsValues with(SettingDefinition<T> definition, T value) {
        T clamped = definition.clamp(value);
        if (!clamped.equals(value)) {
            LOGGER.info("[Modules] Clamped {} from {} to {}", definition.key(), value, clamped);
        }
        DataResult<JsonElement> result =
                definition.valueCodec().encodeStart(JsonOps.INSTANCE, clamped);
        Optional<JsonElement> element = result.result();
        if (element.isEmpty()) {
            LOGGER.warn("[Modules] Could not encode value for {} — leaving unchanged", definition.key());
            return this;
        }
        Map<Identifier, JsonElement> next = new LinkedHashMap<>(encoded);
        next.put(definition.key(), element.get());
        return new ModuleSettingsValues(next);
    }

    /**
     * Drops anything the schema does not define and clamps what it does — the shape stored on disk is not
     * trusted, because it may have been written by an older version or edited by hand.
     */
    public ModuleSettingsValues validated(ModuleSettingsSchema schema) {
        Map<Identifier, JsonElement> next = new LinkedHashMap<>();
        encoded.forEach((key, json) -> {
            SettingDefinition<?> definition = schema.get(key);
            if (definition == null) {
                LOGGER.warn("[Modules] Dropping value for unknown setting {}", key);
                return;
            }
            next.put(key, reclamp(definition, json));
        });
        return next.equals(encoded) ? this : new ModuleSettingsValues(next);
    }

    /** Re-encodes a stored value through its definition so an out-of-range number comes back bounded. */
    private static <T> JsonElement reclamp(SettingDefinition<T> definition,
                                                           JsonElement json) {
        Optional<T> parsed = definition.valueCodec().parse(JsonOps.INSTANCE, json).result();
        if (parsed.isEmpty()) {
            return json; // unreadable; get() will fall back to the default and log
        }
        T clamped = definition.clamp(parsed.get());
        if (clamped.equals(parsed.get())) {
            return json;
        }
        LOGGER.info("[Modules] Clamped stored value for {} to {}", definition.key(), clamped);
        return definition.valueCodec().encodeStart(JsonOps.INSTANCE, clamped).result().orElse(json);
    }

    @Nullable
    public JsonElement rawValue(Identifier key) {
        return encoded.get(key);
    }

    public static ModuleSettingsValues ofRaw(Map<Identifier, JsonElement> raw) {
        return raw.isEmpty() ? EMPTY : new ModuleSettingsValues(raw);
    }
}
