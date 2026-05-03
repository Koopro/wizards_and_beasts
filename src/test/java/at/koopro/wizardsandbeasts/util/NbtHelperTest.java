package at.koopro.wizardsandbeasts.util;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtHelperTest {

    @Test
    void stringSet_roundTrip() {
        Set<String> original = new HashSet<>(Set.of("a", "b", "c"));
        CompoundTag tag = new CompoundTag();
        NbtHelper.saveStringSet(tag, "s", original);
        Set<String> loaded = NbtHelper.loadStringSet(tag, "s");
        assertEquals(original, loaded);
    }

    @Test
    void stringLongMap_roundTrip() {
        Map<String, Long> original = new HashMap<>();
        original.put("x", 1L);
        original.put("y", 42L);
        CompoundTag tag = new CompoundTag();
        NbtHelper.saveStringLongMap(tag, "m", original);
        Map<String, Long> loaded = NbtHelper.loadStringLongMap(tag, "m");
        assertEquals(original, loaded);
    }

    @Test
    void loadEnum_invalidFallsBack() {
        CompoundTag tag = new CompoundTag();
        tag.putString("e", "NOT_A_REAL_ENUM_NAME");
        TestEnum v = NbtHelper.loadEnum(tag, "e", TestEnum.class, TestEnum.A);
        assertEquals(TestEnum.A, v);
    }

    @Test
    void saveNullableString_absentWhenNull() {
        CompoundTag tag = new CompoundTag();
        NbtHelper.saveNullableString(tag, "k", null);
        assertTrue(tag.getString("k").isEmpty());
    }

    private enum TestEnum {
        A, B
    }
}
