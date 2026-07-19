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
 * @param selected  the wheel's armed ACTIVE ability, or {@code null}
 * @param pinned    the quick-slot ability, or {@code null}
 * @param toggles   currently-on TOGGLE abilities
 * @param cooldowns ability id → game-time tick at which the cooldown expires (stamps in the past are stale)
 */
@NullMarked
public record AbilitySelectionState(
        @Nullable Identifier selected,
        @Nullable Identifier pinned,
        Set<Identifier> toggles,
        Map<Identifier, Long> cooldowns) {

    public static final AbilitySelectionState EMPTY =
            new AbilitySelectionState(null, null, Set.of(), Map.of());

    public AbilitySelectionState {
        toggles = Set.copyOf(toggles);
        cooldowns = Map.copyOf(cooldowns);
    }

    public static final Codec<AbilitySelectionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("selected").forGetter(s -> Optional.ofNullable(s.selected)),
            Identifier.CODEC.optionalFieldOf("pinned").forGetter(s -> Optional.ofNullable(s.pinned)),
            Identifier.CODEC.listOf().optionalFieldOf("toggles", java.util.List.of())
                    .forGetter(s -> java.util.List.copyOf(s.toggles)),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(AbilitySelectionState::cooldowns)
    ).apply(instance, (selected, pinned, toggles, cooldowns) -> new AbilitySelectionState(
            selected.orElse(null),
            pinned.orElse(null),
            new LinkedHashSet<>(toggles),
            cooldowns)));

    public AbilitySelectionState withSelected(@Nullable Identifier id) {
        return new AbilitySelectionState(id, pinned, toggles, cooldowns);
    }

    public AbilitySelectionState withPinned(@Nullable Identifier id) {
        return new AbilitySelectionState(selected, id, toggles, cooldowns);
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
        return new AbilitySelectionState(selected, pinned, next, cooldowns);
    }

    /** Returns a copy with {@code id}'s cooldown expiring at {@code expiryGameTime}; a 0/negative expiry clears it. */
    public AbilitySelectionState withCooldown(Identifier id, long expiryGameTime) {
        LinkedHashMap<Identifier, Long> next = new LinkedHashMap<>(cooldowns);
        if (expiryGameTime <= 0) {
            next.remove(id);
        } else {
            next.put(id, expiryGameTime);
        }
        return new AbilitySelectionState(selected, pinned, toggles, next);
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
        return cooldowns.isEmpty() ? this : new AbilitySelectionState(selected, pinned, toggles, Map.of());
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
                : new AbilitySelectionState(selected, pinned, toggles, next);
    }
}
