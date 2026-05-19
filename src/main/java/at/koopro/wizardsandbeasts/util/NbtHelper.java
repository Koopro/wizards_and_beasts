package at.koopro.wizardsandbeasts.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Static helpers for common NBT serialization patterns.
 * Uses the MC 1.21.11 Optional-based CompoundTag API.
 */
public final class NbtHelper {

    private NbtHelper() {}

    // ── String Sets ──────────────────────────────────────────────────────

    public static void saveStringSet(CompoundTag tag, String key, Set<String> set) {
        ListTag list = new ListTag();
        for (String s : set) {
            list.add(StringTag.valueOf(s));
        }
        tag.put(key, list);
    }

    public static Set<String> loadStringSet(CompoundTag tag, String key) {
        Set<String> set = new HashSet<>();
        ListTag list = tag.getList(key).orElse(new ListTag());
        for (int i = 0; i < list.size(); i++) {
            list.getString(i).ifPresent(set::add);
        }
        return set;
    }

    // ── Map<String, Long> ────────────────────────────────────────────────

    public static void saveStringLongMap(CompoundTag tag, String key, Map<String, Long> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey());
            entryTag.putLong("value", entry.getValue());
            list.add(entryTag);
        }
        tag.put(key, list);
    }

    public static Map<String, Long> loadStringLongMap(CompoundTag tag, String key) {
        Map<String, Long> map = new HashMap<>();
        ListTag list = tag.getList(key).orElse(new ListTag());
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(entry -> {
                String id = entry.getString("id").orElse("");
                long value = entry.getLong("value").orElse(0L);
                if (!id.isEmpty()) map.put(id, value);
            });
        }
        return map;
    }

    // ── Map<String, Integer> ─────────────────────────────────────────────

    public static void saveStringIntMap(CompoundTag tag, String key, Map<String, Integer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey());
            entryTag.putInt("value", entry.getValue());
            list.add(entryTag);
        }
        tag.put(key, list);
    }

    public static Map<String, Integer> loadStringIntMap(CompoundTag tag, String key) {
        Map<String, Integer> map = new HashMap<>();
        ListTag list = tag.getList(key).orElse(new ListTag());
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(entry -> {
                String id = entry.getString("id").orElse("");
                int value = entry.getInt("value").orElse(0);
                if (!id.isEmpty()) map.put(id, value);
            });
        }
        return map;
    }

    // ── Map<String, Float> ───────────────────────────────────────────────

    public static void saveStringFloatMap(CompoundTag tag, String key, Map<String, Float> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey());
            entryTag.putFloat("value", entry.getValue());
            list.add(entryTag);
        }
        tag.put(key, list);
    }

    public static Map<String, Float> loadStringFloatMap(CompoundTag tag, String key) {
        Map<String, Float> map = new HashMap<>();
        ListTag list = tag.getList(key).orElse(new ListTag());
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(entry -> {
                String id = entry.getString("id").orElse("");
                float value = entry.getFloat("value").orElse(0.0f);
                if (!id.isEmpty()) map.put(id, value);
            });
        }
        return map;
    }

    // ── Map<String, String> (parallel ListTags) ──────────────────────────

    public static void saveStringStringMap(CompoundTag tag, String keysName,
                                           String valuesName, Map<String, String> map) {
        if (map.isEmpty()) return;
        ListTag keys = new ListTag();
        ListTag values = new ListTag();
        for (var entry : map.entrySet()) {
            keys.add(StringTag.valueOf(entry.getKey()));
            values.add(StringTag.valueOf(entry.getValue()));
        }
        tag.put(keysName, keys);
        tag.put(valuesName, values);
    }

    public static Map<String, String> loadStringStringMap(CompoundTag tag, String keysName,
                                                          String valuesName) {
        Map<String, String> map = new HashMap<>();
        ListTag keys = tag.getList(keysName).orElse(new ListTag());
        ListTag vals = tag.getList(valuesName).orElse(new ListTag());
        int count = Math.min(keys.size(), vals.size());
        for (int i = 0; i < count; i++) {
            final int idx = i;
            keys.getString(i).ifPresent(k ->
                    vals.getString(idx).ifPresent(v -> map.put(k, v)));
        }
        return map;
    }

    // ── Enums ────────────────────────────────────────────────────────────

    public static <E extends Enum<E>> void saveEnum(CompoundTag tag, String key, @Nullable E value) {
        if (value != null) {
            tag.putString(key, value.name());
        }
    }

    public static <E extends Enum<E>> E loadEnum(CompoundTag tag, String key,
                                                  Class<E> enumClass, E fallback) {
        return tag.getString(key).map(s -> {
            try { return Enum.valueOf(enumClass, s); }
            catch (IllegalArgumentException e) { return fallback; }
        }).orElse(fallback);
    }

    @Nullable
    public static <E extends Enum<E>> E loadEnumNullable(CompoundTag tag, String key,
                                                          Class<E> enumClass) {
        return tag.getString(key).map(s -> {
            try { return Enum.valueOf(enumClass, s); }
            catch (IllegalArgumentException e) { return (E) null; }
        }).orElse(null);
    }

    // ── Nullable Strings ─────────────────────────────────────────────────

    public static void saveNullableString(CompoundTag tag, String key, @Nullable String value) {
        if (value != null) {
            tag.putString(key, value);
        }
    }

    @Nullable
    public static String loadNullableString(CompoundTag tag, String key) {
        return tag.getString(key).orElse(null);
    }
}
