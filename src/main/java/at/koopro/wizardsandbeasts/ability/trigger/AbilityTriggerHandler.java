package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.AbilityResolver;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityInput;
import at.koopro.wizardsandbeasts.ability.def.AbilityType;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionHelper;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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

    /** Blocks of tolerance added to a definition's pick range when re-validating a client target. */
    private static final double TARGET_RANGE_SLACK = 4.0;

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

    /**
     * Arms {@code id} without acting on it — the hover gesture. Unlike {@link #confirmSelection} this never
     * flips a TOGGLE, so sweeping the cursor across the wheel cannot transform the player; it only moves
     * which ability the use key will fire.
     */
    public static void arm(ServerPlayer player, Identifier id) {
        if (validateWheel(player, id) != null) {
            AbilitySelectionHelper.select(player, id);
        }
    }

    /**
     * Pin gesture on the hovered entry: cycles it through the quick slots and back out —
     * unbound → slot 0 → slot 1 → … → unbound. One gesture reaches every slot, so the wheel needs no
     * modifier keys or extra UI.
     */
    public static void cycleQuickSlot(ServerPlayer player, Identifier id) {
        AbilityDefinition def = validateWheel(player, id);
        if (def == null) {
            return;
        }
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        int next = state.slotOf(id) + 1; // SLOT_SELECTED (-1) → 0
        if (AbilitySelectionState.isValidSlot(next)) {
            AbilitySelectionHelper.setQuickSlot(player, next, id);
        } else {
            AbilitySelectionHelper.setQuickSlot(player, state.slotOf(id), null);
        }
    }

    /** Untargeted use — equivalent to {@link #use(ServerPlayer, int, AbilityTarget)} with no pick. */
    public static void use(ServerPlayer player, int slot) {
        use(player, slot, AbilityTarget.NONE);
    }

    /**
     * Fires one ability: {@code slot} is {@link AbilitySelectionState#SLOT_SELECTED} for the use key (the
     * wheel's armed selection) or a quick-slot index for one of the quick keys. TOGGLE targets flip instead.
     * {@code target} is the client's pick and is re-validated here against the definition's
     * {@code input} — wrong kind or out-of-range drops the request rather than approximating it.
     */
    public static void use(ServerPlayer player, int slot, AbilityTarget target) {
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        Identifier armed = slot == AbilitySelectionState.SLOT_SELECTED
                ? state.selected()
                : state.quickSlot(slot);
        if (armed == null) {
            return;
        }
        AbilityDefinition def = validateWheel(player, armed);
        if (def == null) {
            return;
        }
        if (def.type() == AbilityType.TOGGLE) {
            flipToggle(player, def);
            return;
        }
        AbilityTarget validated = validateTarget(player, def, target);
        if (validated == null) {
            return;
        }
        activate(player, def, validated);
    }

    /** Direct toggle request (e.g. from a future dedicated binding). */
    public static void toggle(ServerPlayer player, Identifier id) {
        AbilityDefinition def = validateWheel(player, id);
        if (def != null && def.type() == AbilityType.TOGGLE) {
            flipToggle(player, def);
        }
    }

    // ── internals ──

    private static void activate(ServerPlayer player, AbilityDefinition def, AbilityTarget target) {
        long gameTime = player.level().getGameTime();
        AbilitySelectionState state = AbilitySelectionHelper.get(player);
        if (state.isOnCooldown(def.id(), gameTime)) {
            return;
        }
        boolean fired = AbilityBehaviors.get(def.id()).onActivate(player, def, target);
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
        AbilityBehavior behavior = AbilityBehaviors.get(def.id());
        boolean owned = behavior.ownsToggleState();
        boolean nowOn = !(owned ? behavior.isToggledOn(player, def) : state.isToggled(def.id()));
        if (!owned) {
            AbilitySelectionHelper.setToggle(player, def.id(), nowOn);
        }
        behavior.onToggle(player, def, nowOn);
        if (owned) {
            // The owner may have refused; re-push so the wheel mirrors whatever actually happened.
            AbilitySelectionHelper.sync(player);
        }
        if (def.cooldownTicks() > 0) {
            AbilitySelectionHelper.setCooldown(player, def.id(), gameTime + def.cooldownTicks());
        }
    }

    /**
     * Server-side re-validation of the client's pick. Returns the target to dispatch, or {@code null} to drop
     * the request. Untargeted abilities collapse to {@link AbilityTarget#NONE} regardless of what was sent.
     */
    @Nullable
    private static AbilityTarget validateTarget(ServerPlayer player, AbilityDefinition def, AbilityTarget target) {
        AbilityInput input = def.input();
        if (!input.requiresTarget()) {
            return AbilityTarget.NONE;
        }
        if (!target.matches(input.targeting())) {
            return null;
        }
        // Slack absorbs the client's eye-height/partial-tick offset; the behaviors clamp precisely themselves.
        double maxDistance = input.range() + TARGET_RANGE_SLACK;
        return switch (input.targeting()) {
            case BLOCK -> {
                Vec3 position = target.position();
                yield position != null && player.position().distanceTo(position) <= maxDistance ? target : null;
            }
            case ENTITY -> {
                Entity picked = player.level().getEntity(target.entityId());
                yield picked != null && player.distanceTo(picked) <= maxDistance ? target : null;
            }
            case NONE -> AbilityTarget.NONE;
        };
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
