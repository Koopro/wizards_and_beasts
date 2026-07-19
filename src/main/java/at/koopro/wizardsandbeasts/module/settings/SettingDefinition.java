package at.koopro.wizardsandbeasts.module.settings;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One tunable knob belonging to a module: what it is called, what type it holds, its default, and how to
 * read and bound a value of that type.
 *
 * <p>Sealed so the admin UI and the command parser can exhaust over the concrete kinds. Every definition
 * owns its own {@link #valueCodec()}, which is what lets values be stored and synced generically without
 * anything downstream knowing the type.
 *
 * <p>This prompt ships the framework only — every module's schema is empty, so nothing here is wired to
 * gameplay yet.
 *
 * @param <T> the value type this setting holds
 */
@NullMarked
public sealed interface SettingDefinition<T>
        permits SettingDefinition.BoolSetting,
                SettingDefinition.IntRangeSetting,
                SettingDefinition.DoubleRangeSetting,
                SettingDefinition.EnumSetting {

    /** Stable key, unique within the owning module's schema. Persisted and sent on the wire. */
    Identifier key();

    /** Translation key for the display name. */
    String nameKey();

    /** Value used when a stored value is absent. */
    T defaultValue();

    /** Codec for this setting's value, used for both persistence and sync. */
    Codec<T> valueCodec();

    /**
     * Brings a value inside this setting's bounds. Returns the value unchanged when already valid; callers
     * compare identity/equality to detect that a clamp happened so it can be logged.
     */
    T clamp(T value);

    default Component displayName() {
        return Component.translatable(nameKey());
    }

    // ── kinds ──

    record BoolSetting(Identifier key, String nameKey, Boolean defaultValue) implements SettingDefinition<Boolean> {
        @Override
        public Codec<Boolean> valueCodec() {
            return Codec.BOOL;
        }

        @Override
        public Boolean clamp(Boolean value) {
            return value;
        }
    }

    record IntRangeSetting(Identifier key, String nameKey, Integer defaultValue, int min, int max)
            implements SettingDefinition<Integer> {
        public IntRangeSetting {
            if (min > max) {
                throw new IllegalArgumentException("min > max for setting " + key);
            }
        }

        @Override
        public Codec<Integer> valueCodec() {
            return Codec.INT;
        }

        @Override
        public Integer clamp(Integer value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    record DoubleRangeSetting(Identifier key, String nameKey, Double defaultValue, double min, double max)
            implements SettingDefinition<Double> {
        public DoubleRangeSetting {
            if (min > max) {
                throw new IllegalArgumentException("min > max for setting " + key);
            }
        }

        @Override
        public Codec<Double> valueCodec() {
            return Codec.DOUBLE;
        }

        @Override
        public Double clamp(Double value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /**
     * A choice from an enum. The enum must be {@link StringRepresentable} so the stored form is a stable
     * name rather than an ordinal that shifts when constants are reordered.
     */
    record EnumSetting<E extends Enum<E> & StringRepresentable>(
            Identifier key, String nameKey, E defaultValue, Class<E> type) implements SettingDefinition<E> {

        @Override
        public Codec<E> valueCodec() {
            return StringRepresentable.fromEnum(() -> type.getEnumConstants());
        }

        @Override
        public E clamp(E value) {
            return value;
        }

        /** The permitted values, for UI and command suggestions. */
        public E[] options() {
            return type.getEnumConstants();
        }

        @Nullable
        public E parse(String raw) {
            for (E constant : type.getEnumConstants()) {
                if (constant.getSerializedName().equalsIgnoreCase(raw)) {
                    return constant;
                }
            }
            return null;
        }
    }
}
