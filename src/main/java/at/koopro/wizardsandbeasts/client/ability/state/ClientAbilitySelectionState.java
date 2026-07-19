package at.koopro.wizardsandbeasts.client.ability.state;

import at.koopro.wizardsandbeasts.network.ability.AbilitySelectionSyncS2CPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client mirror of the server's per-player wheel state ({@link AbilitySelectionSyncS2CPayload}). The wheel
 * reads exclusively from here — usable ids, selection, pin, toggles, cooldowns — so the client never derives
 * grants itself. Class-init-safe (only common-typed static state).
 */
@NullMarked
public final class ClientAbilitySelectionState {

    private static volatile Set<Identifier> usable = Set.of();
    @Nullable
    private static volatile Identifier selected;
    /** Fixed-length by slot index; a {@code null} entry is an unbound slot. */
    private static volatile List<Identifier> quickSlots = List.of();
    private static volatile Set<Identifier> toggles = Set.of();
    private static volatile Map<Identifier, Long> cooldowns = Map.of();

    private ClientAbilitySelectionState() {}

    public static void handle(AbilitySelectionSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> apply(payload));
    }

    private static void apply(AbilitySelectionSyncS2CPayload payload) {
        usable = new LinkedHashSet<>(payload.usable());
        selected = payload.selected();
        quickSlots = new ArrayList<>(payload.quickSlots());
        toggles = new LinkedHashSet<>(payload.toggles());
        cooldowns = new LinkedHashMap<>(payload.cooldowns());
    }

    public static Set<Identifier> usable() {
        return usable;
    }

    public static boolean isUsable(Identifier id) {
        return usable.contains(id);
    }

    @Nullable
    public static Identifier selected() {
        return selected;
    }

    /** The ability bound to quick slot {@code slot}, or {@code null} if unbound / out of range. */
    @Nullable
    public static Identifier quickSlot(int slot) {
        List<Identifier> slots = quickSlots;
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
    }

    /** The slot {@code id} occupies, or {@code -1} — drives the wheel's slot-number badge. */
    public static int slotOf(Identifier id) {
        List<Identifier> slots = quickSlots;
        for (int i = 0; i < slots.size(); i++) {
            if (id.equals(slots.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isToggled(Identifier id) {
        return toggles.contains(id);
    }

    public static boolean isOnCooldown(Identifier id, long gameTime) {
        Long expiry = cooldowns.get(id);
        return expiry != null && expiry > gameTime;
    }

    /** Fraction of cooldown remaining in {@code [0,1]} for a radial sweep; 0 if not on cooldown. */
    public static float cooldownFraction(Identifier id, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return 0f;
        }
        Long expiry = cooldowns.get(id);
        if (expiry == null || expiry <= gameTime) {
            return 0f;
        }
        float remaining = (expiry - gameTime) / (float) cooldownTicks;
        return Math.max(0f, Math.min(1f, remaining));
    }

    public static void clear() {
        usable = Set.of();
        selected = null;
        quickSlots = List.of();
        toggles = Set.of();
        cooldowns = Map.of();
    }
}
