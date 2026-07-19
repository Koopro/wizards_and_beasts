package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.AbilityResolver;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityType;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionHelper;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative entry point for every wheel/keybind request. Each public method re-validates grant +
 * module + type + cooldown from server truth (never the client's claim) before mutating state or dispatching
 * to an {@link AbilityBehavior}. PASSIVE/CONTEXTUAL abilities are rejected here — they are never selectable,
 * pinnable, or triggerable (§2.3).
 */
@NullMarked
public final class AbilityTriggerHandler {

    private AbilityTriggerHandler() {}

    /** Wheel confirm on the hovered entry: TOGGLE flips immediately; ACTIVE is armed as the selection. */
    public static void confirmSelection(ServerPlayer player, Identifier id) {
        AbilityDefinition def = validateWheel(player, id);
        if (def == null) {
            return;
        }
        if (def.type() == AbilityType.TOGGLE) {
            flipToggle(player, def);
        } else { // ACTIVE
            AbilitySelectionHelper.select(player, id);
        }
    }

    /** Pin gesture on the hovered entry: pins it, or unpins if it is already the pinned ability. */
    public static void togglePin(ServerPlayer player, Identifier id) {
        AbilityDefinition def = validateWheel(player, id);
        if (def == null) {
            return;
        }
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        boolean alreadyPinned = id.equals(state.pinned());
        AbilitySelectionHelper.pin(player, alreadyPinned ? null : id);
    }

    /** Use key: fire the armed selection. Quick key: fire the pinned ability. TOGGLE targets flip instead. */
    public static void use(ServerPlayer player, boolean quick) {
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        Identifier target = quick ? state.pinned() : state.selected();
        if (target == null) {
            return;
        }
        AbilityDefinition def = validateWheel(player, target);
        if (def == null) {
            return;
        }
        if (def.type() == AbilityType.TOGGLE) {
            flipToggle(player, def);
        } else {
            activate(player, def);
        }
    }

    /** Direct toggle request (e.g. from a future dedicated binding). */
    public static void toggle(ServerPlayer player, Identifier id) {
        AbilityDefinition def = validateWheel(player, id);
        if (def != null && def.type() == AbilityType.TOGGLE) {
            flipToggle(player, def);
        }
    }

    // ── internals ──

    private static void activate(ServerPlayer player, AbilityDefinition def) {
        long gameTime = player.level().getGameTime();
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        if (state.isOnCooldown(def.id(), gameTime)) {
            return;
        }
        boolean fired = AbilityBehaviors.get(def.id()).onActivate(player, def);
        if (fired && def.cooldownTicks() > 0) {
            AbilitySelectionHelper.setCooldown(player, def.id(), gameTime + def.cooldownTicks());
        }
    }

    private static void flipToggle(ServerPlayer player, AbilityDefinition def) {
        long gameTime = player.level().getGameTime();
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        if (state.isOnCooldown(def.id(), gameTime)) {
            return;
        }
        boolean nowOn = !state.isToggled(def.id());
        AbilitySelectionHelper.setToggle(player, def.id(), nowOn);
        AbilityBehaviors.get(def.id()).onToggle(player, def, nowOn);
        if (def.cooldownTicks() > 0) {
            AbilitySelectionHelper.setCooldown(player, def.id(), gameTime + def.cooldownTicks());
        }
    }

    /**
     * Returns the definition iff it exists, is wheel-eligible (ACTIVE/TOGGLE), and is usable (granted +
     * module-permitted) by the player; otherwise {@code null}. Server truth only.
     */
    @Nullable
    private static AbilityDefinition validateWheel(ServerPlayer player, Identifier id) {
        AbilityDefinition def = AbilityResolver.definition(id);
        if (def == null || !def.isWheelEligible()) {
            return null;
        }
        return AbilityResolver.isUsable(player, def) ? def : null;
    }
}
