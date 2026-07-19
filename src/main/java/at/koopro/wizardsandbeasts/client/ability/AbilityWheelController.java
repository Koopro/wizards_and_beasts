package at.koopro.wizardsandbeasts.client.ability;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.def.AbilityInput;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityChargeState;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilitySelectionState;
import at.koopro.wizardsandbeasts.client.ability.wheel.AbilityWheelScreen;
import at.koopro.wizardsandbeasts.network.ability.AbilityUseC2SPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Client input driver for the ability framework. Opens the {@link AbilityWheelScreen} while the wheel key is
 * held, and drives the use/quick keys according to the armed ability's {@link AbilityInput}:
 *
 * <ul>
 *   <li>no charge, no target — fire on key press (the original framework behavior);</li>
 *   <li>charge and/or target — hold the key, re-pick the crosshair target every tick once the charge window
 *       has elapsed, and fire on release.</li>
 * </ul>
 *
 * This is what lets Apparition (hold, aim a destination) and Legilimency (hold, aim a mind) ride the wheel
 * with the exact input feel they had on their own keybinds. The pick is a request only — the server
 * re-validates kind and range before dispatching.
 */
@NullMarked
public final class AbilityWheelController {

    /** Which slot owns the current charge, so no two keys can charge at once. */
    private static int chargingSlot = AbilitySelectionState.SLOT_SELECTED;
    private static boolean charging;

    private AbilityWheelController() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            cancelCharge();
            return;
        }

        drive(mc.player, AbilityFrameworkKeyBindings.ABILITY_USE, AbilitySelectionState.SLOT_SELECTED);
        for (int slot = 0; slot < AbilityFrameworkKeyBindings.QUICK_SLOTS.length; slot++) {
            drive(mc.player, AbilityFrameworkKeyBindings.QUICK_SLOTS[slot], slot);
        }

        if (!AbilityFrameworkKeyBindings.ABILITY_WHEEL.isUnbound()
                && AbilityFrameworkKeyBindings.ABILITY_WHEEL.isDown()) {
            cancelCharge();
            mc.setScreen(new AbilityWheelScreen());
        }
    }

    private static void drive(LocalPlayer player, KeyMapping key, int slot) {
        AbilityDefinition def = armed(slot);
        if (def == null) {
            drain(key);
            releaseIfOwner(slot);
            return;
        }
        AbilityInput input = def.input();
        if (!input.requiresCharge()) {
            // Press-to-fire: pick at the moment of the press, if this ability wants a target at all.
            while (key.consumeClick()) {
                AbilityTarget target = pick(player, input);
                if (!input.requiresTarget() || target != AbilityTarget.NONE) {
                    send(slot, target);
                }
            }
            return;
        }

        boolean down = !key.isUnbound() && key.isDown();
        if (down) {
            if (!charging || chargingSlot != slot) {
                charging = true;
                chargingSlot = slot;
                ClientAbilityChargeState.begin(def.id(), input.chargeTicks());
            }
            ClientAbilityChargeState.tick();
            if (ClientAbilityChargeState.isCharged()) {
                AbilityTarget target = pick(player, input);
                store(target);
            }
        } else if (charging && chargingSlot == slot) {
            if (ClientAbilityChargeState.isCharged()) {
                AbilityTarget target = stored();
                if (!input.requiresTarget() || target != AbilityTarget.NONE) {
                    send(slot, target);
                }
            }
            cancelCharge();
        }
        drain(key);
    }

    @Nullable
    private static AbilityDefinition armed(int slot) {
        Identifier id = slot == AbilitySelectionState.SLOT_SELECTED
                ? ClientAbilitySelectionState.selected()
                : ClientAbilitySelectionState.quickSlot(slot);
        if (id == null || !ClientAbilitySelectionState.isUsable(id)) {
            return null;
        }
        return AbilityDefinitionRegistry.get(id);
    }

    /** Crosshair pick for {@code input}'s targeting mode; {@link AbilityTarget#NONE} when nothing qualifies. */
    private static AbilityTarget pick(LocalPlayer player, AbilityInput input) {
        return switch (input.targeting()) {
            case NONE -> AbilityTarget.NONE;
            case BLOCK -> {
                HitResult hit = player.pick(input.range(), 0.0f, false);
                if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                    yield AbilityTarget.ofBlock(blockHit.getBlockPos(), blockHit.getLocation());
                }
                // No block under the crosshair: aim at the end of the ray, as the dedicated bind did.
                Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(input.range()));
                yield AbilityTarget.ofBlock(BlockPos.containing(end), end);
            }
            case ENTITY -> {
                HitResult hit = player.pick(input.range(), 0.0f, false);
                yield hit instanceof EntityHitResult entityHit
                        ? AbilityTarget.ofEntity(entityHit.getEntity().getId())
                        : AbilityTarget.NONE;
            }
        };
    }

    private static void store(AbilityTarget target) {
        switch (target.kind()) {
            case BLOCK -> {
                BlockPos blockPos = target.blockPos();
                Vec3 position = target.position();
                if (blockPos != null && position != null) {
                    ClientAbilityChargeState.setBlockTarget(blockPos, position);
                }
            }
            case ENTITY -> ClientAbilityChargeState.setEntityTarget(target.entityId());
            case NONE -> ClientAbilityChargeState.clearTarget();
        }
    }

    private static AbilityTarget stored() {
        BlockPos blockPos = ClientAbilityChargeState.targetBlockPos();
        Vec3 position = ClientAbilityChargeState.targetPosition();
        if (blockPos != null && position != null) {
            return AbilityTarget.ofBlock(blockPos, position);
        }
        int entityId = ClientAbilityChargeState.targetEntityId();
        return entityId == 0 ? AbilityTarget.NONE : AbilityTarget.ofEntity(entityId);
    }

    private static void releaseIfOwner(int slot) {
        if (charging && chargingSlot == slot) {
            cancelCharge();
        }
    }

    private static void cancelCharge() {
        charging = false;
        chargingSlot = AbilitySelectionState.SLOT_SELECTED;
        ClientAbilityChargeState.clear();
    }

    /** Drops queued presses so a held key does not leak a click burst into the next tick. */
    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // discarded
        }
    }

    private static void send(int slot, AbilityTarget target) {
        ClientPacketDistributor.sendToServer(new AbilityUseC2SPayload(slot, target));
    }
}
