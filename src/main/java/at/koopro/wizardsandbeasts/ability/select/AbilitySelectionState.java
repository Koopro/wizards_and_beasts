package at.koopro.wizardsandbeasts.ability.select;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-player wheel/selection state — the generic ability-slot data the framework tracks. Distinct from the
 * legacy per-ability {@code ability.data.PlayerAbilityData} (which holds specific Apparition/Legilimency/
 * Animagus fields); this is the new, ability-agnostic selection layer, hence a separate attachment.
 *
 * @param selected   the wheel's armed ACTIVE ability, or {@code null}
 * @param quickSlots slot index → ability bound to that quick key; absent index = empty slot
 * @param toggles    currently-on TOGGLE abilities
 * @param cooldowns  ability id → game-time tick at which the cooldown expires (stamps in the past are stale)
 */
@NullMarked
public record AbilitySelectionState(
        @Nullable Identifier selected,
        Map<Integer, Identifier> quickSlots,
        Set<Identifier> toggles,
        Map<Identifier, Long> cooldowns) {

    /** Number of one-press quick slots, each with its own keybind. Slot indices are {@code [0, COUNT)}. */
    public static final int QUICK_SLOT_COUNT = 3;

    /** Slot value meaning "the wheel's armed selection", not a quick slot — the use key's slot. */
    public static final int SLOT_SELECTED = -1;

    public static final AbilitySelectionState EMPTY =
            new AbilitySelectionState(null, Map.of(), Set.of(), Map.of());

    public AbilitySelectionState {
        quickSlots = Map.copyOf(quickSlots);
        toggles = Set.copyOf(toggles);
        cooldowns = Map.copyOf(cooldowns);
    }

    /** One occupied quick slot. Serialized as a list so the slot index survives NBT without string keys. */
    private record QuickSlotEntry(int slot, Identifier ability) {
        static final Codec<QuickSlotEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(QuickSlotEntry::slot),
                Identifier.CODEC.fieldOf("ability").forGetter(QuickSlotEntry::ability)
        ).apply(instance, QuickSlotEntry::new));
    }

    public static final Codec<AbilitySelectionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("selected").forGetter(s -> Optional.ofNullable(s.selected)),
            QuickSlotEntry.CODEC.listOf().optionalFieldOf("quickSlots", java.util.List.of())
                    .forGetter(AbilitySelectionState::quickSlotEntries),
            // Legacy single-pin field; folded into slot 0 on read, never written back.
            Identifier.CODEC.optionalFieldOf("pinned").forGetter(s -> Optional.<Identifier>empty()),
            Identifier.CODEC.listOf().optionalFieldOf("toggles", java.util.List.of())
                    .forGetter(s -> java.util.List.copyOf(s.toggles)),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(AbilitySelectionState::cooldowns)
    ).apply(instance, (selected, quickSlots, legacyPinned, toggles, cooldowns) -> new AbilitySelectionState(
            selected.orElse(null),
            mergeQuickSlots(quickSlots, legacyPinned.orElse(null)),
            new LinkedHashSet<>(toggles),
            cooldowns)));

    private static Map<Integer, Identifier> mergeQuickSlots(java.util.List<QuickSlotEntry> entries,
                                                            @Nullable Identifier legacyPinned) {
        LinkedHashMap<Integer, Identifier> map = new LinkedHashMap<>();
        for (QuickSlotEntry entry : entries) {
            if (isValidSlot(entry.slot())) {
                map.put(entry.slot(), entry.ability());
            }
        }
        // A save written before quick slots existed carries "pinned"; it becomes the first quick slot.
        if (legacyPinned != null) {
            map.putIfAbsent(0, legacyPinned);
        }
        return map;
    }

    private java.util.List<QuickSlotEntry> quickSlotEntries() {
        java.util.List<QuickSlotEntry> out = new java.util.ArrayList<>(quickSlots.size());
        for (Map.Entry<Integer, Identifier> entry : quickSlots.entrySet()) {
            out.add(new QuickSlotEntry(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < QUICK_SLOT_COUNT;
    }

    public AbilitySelectionState withSelected(@Nullable Identifier id) {
        return new AbilitySelectionState(id, quickSlots, toggles, cooldowns);
    }

    /** The ability in {@code slot}, or {@code null} if the slot is empty or out of range. */
    @Nullable
    public Identifier quickSlot(int slot) {
        return quickSlots.get(slot);
    }

    /** The slot {@code id} occupies, or {@link #SLOT_SELECTED} if it occupies none. */
    public int slotOf(Identifier id) {
        for (Map.Entry<Integer, Identifier> entry : quickSlots.entrySet()) {
            if (entry.getValue().equals(id)) {
                return entry.getKey();
            }
        }
        return SLOT_SELECTED;
    }

    /**
     * Binds {@code id} to {@code slot} ({@code null} clears it). An ability lives in at most one slot, so it
     * is removed from any other slot it held.
     */
    public AbilitySelectionState withQuickSlot(int slot, @Nullable Identifier id) {
        if (!isValidSlot(slot)) {
            return this;
        }
        LinkedHashMap<Integer, Identifier> next = new LinkedHashMap<>(quickSlots);
        next.remove(slot);
        if (id != null) {
            next.values().removeIf(id::equals);
            next.put(slot, id);
        }
        return new AbilitySelectionState(selected, next, toggles, cooldowns);
    }

    public boolean isToggled(Identifier id) {
        return toggles.contains(id);
    }

    public AbilitySelectionState withToggle(Identifier id, boolean on) {
        LinkedHashSet<Identifier> next = new LinkedHashSet<>(toggles);
        if (on) {
            next.add(id);
        } else {
            next.remove(id);
        }
        return new AbilitySelectionState(selected, quickSlots, next, cooldowns);
    }

    /** Returns a copy with {@code id}'s cooldown expiring at {@code expiryGameTime}; a 0/negative expiry clears it. */
    public AbilitySelectionState withCooldown(Identifier id, long expiryGameTime) {
        LinkedHashMap<Identifier, Long> next = new LinkedHashMap<>(cooldowns);
        if (expiryGameTime <= 0) {
            next.remove(id);
        } else {
            next.put(id, expiryGameTime);
        }
        return new AbilitySelectionState(selected, quickSlots, toggles, next);
    }

    public boolean isOnCooldown(Identifier id, long gameTime) {
        Long expiry = cooldowns.get(id);
        return expiry != null && expiry > gameTime;
    }

    /** Remaining cooldown ticks for {@code id} at {@code gameTime}, or 0 if none. */
    public long cooldownRemaining(Identifier id, long gameTime) {
        Long expiry = cooldowns.get(id);
        return expiry == null ? 0L : Math.max(0L, expiry - gameTime);
    }

    /** Drops all cooldown stamps — used on death (§2.2: cooldowns are not carried across death). */
    public AbilitySelectionState withoutCooldowns() {
        return cooldowns.isEmpty() ? this : new AbilitySelectionState(selected, quickSlots, toggles, Map.of());
    }

    /** Removes expired cooldown stamps relative to {@code gameTime}; returns {@code this} if nothing changed. */
    public AbilitySelectionState prunedCooldowns(long gameTime) {
        if (cooldowns.isEmpty()) {
            return this;
        }
        LinkedHashMap<Identifier, Long> next = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Long> entry : cooldowns.entrySet()) {
            if (entry.getValue() > gameTime) {
                next.put(entry.getKey(), entry.getValue());
            }
        }
        return next.size() == cooldowns.size() ? this
                : new AbilitySelectionState(selected, quickSlots, toggles, next);
    }
}
