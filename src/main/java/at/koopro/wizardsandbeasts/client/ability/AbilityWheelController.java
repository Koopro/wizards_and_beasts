package at.koopro.wizardsandbeasts.client.ability;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.def.AbilityInput;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityChargeState;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilitySelectionState;
import at.koopro.wizardsandbeasts.client.ability.wheel.AbilityWheelScreen;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import at.koopro.wizardsandbeasts.network.ability.AbilityUseC2SPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionChargeAbortC2SPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionChargeReleaseC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * Client input driver for the ability framework. Opens the {@link AbilityWheelScreen} on a press of the
 * wheel key — tap to latch it open, or keep holding to confirm on release (the wheel classifies the gesture
 * itself) — and drives the use/quick keys according to the armed ability's {@link AbilityInput}:
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
    /**
     * Whether the charge in progress is timed by the server. Tracked separately from the definition because
     * {@link #cancelCharge()} runs from paths that no longer have one in hand, and a server-timed charge that
     * is dropped locally has to be withdrawn on the server too or it will discharge itself.
     */
    private static boolean chargingServerSide;
    /**
     * True while the server is running a charge that no key press on this client started.
     *
     * <p>Only the destination selector produces one: picking a point begins a seventy-tick anchored charge
     * at a moment when the player is holding nothing, because they were clicking in a GUI. That charge was
     * invisible to this driver — no key-up could release it and {@link #cancelCharge()} could not withdraw
     * it — so the only way it ever ended was by running out its hard cap, which used to mean a catastrophic
     * splinch for doing nothing.
     *
     * <p>Adopted from the server's own phase broadcast rather than from the selector screen, so the flag
     * cannot race the frame the screen closes on, and so a charge begun any other way is picked up too.
     *
     * <p>While it is set, the next press <em>is</em> the release. There is nothing left to hold: the hold
     * already happened, on the server, while the ring closed.
     */
    private static boolean adoptedServerCharge;

    /** Physical wheel-key state last tick — the wheel opens on the rising edge, never while merely held. */
    private static boolean wheelWasDown;
    /** Set when the wheel closes on its own key, so the still-held key cannot immediately reopen it. */
    private static boolean suppressOpenUntilRelease;

    private AbilityWheelController() {}

    /** Called by the wheel when the wheel key itself closed it. */
    public static void suppressOpenUntilRelease() {
        suppressOpenUntilRelease = true;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Polled raw, and before the screen check, so the edge stays accurate while the wheel is open
        // (KeyMapping.isDown() does not report keys held across a Screen).
        boolean wheelDown = isWheelKeyDown(mc);
        boolean rising = wheelDown && !wheelWasDown && !suppressOpenUntilRelease;
        wheelWasDown = wheelDown;
        if (!wheelDown) {
            suppressOpenUntilRelease = false;
        }

        if (mc.player == null || mc.screen != null) {
            cancelCharge();
            return;
        }

        adoptOrphanedCharge(mc.player);

        drive(mc.player, AbilityFrameworkKeyBindings.ABILITY_USE, AbilitySelectionState.SLOT_SELECTED);
        for (int slot = 0; slot < AbilityFrameworkKeyBindings.QUICK_SLOTS.length; slot++) {
            drive(mc.player, AbilityFrameworkKeyBindings.QUICK_SLOTS[slot], slot);
        }

        if (rising) {
            cancelCharge();
            mc.setScreen(new AbilityWheelScreen());
        }
    }

    /**
     * Notices a server-side charge this client is not driving, and arms the next press to commit it.
     *
     * <p>Anchored only. A blink is always started by a key that is still held, so it already has a key-up
     * coming; an anchored jump picked out of the selector does not.
     */
    private static void adoptOrphanedCharge(LocalPlayer player) {
        ClientApparitionPresentationState.Charge live =
                ClientApparitionPresentationState.charge(player.getId());
        if (live == null) {
            adoptedServerCharge = false;
            return;
        }
        if (adoptedServerCharge || charging || live.tier() != ApparitionTier.ANCHORED) {
            return;
        }
        adoptedServerCharge = true;
        // Said once, on the action bar, because the gesture is not guessable: every other ability in the
        // framework is hold-and-release and this one is a single press.
        player.displayClientMessage(
                Component.translatable("apparition.wizards_and_beasts.travel.armed"), true);
    }

    private static boolean isWheelKeyDown(Minecraft mc) {
        InputConstants.Key key = AbilityFrameworkKeyBindings.ABILITY_WHEEL.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    private static void drive(LocalPlayer player, KeyMapping key, int slot) {
        AbilityDefinition def = armed(slot);
        if (def == null) {
            drain(key);
            releaseIfOwner(slot);
            return;
        }
        AbilityInput input = def.input();
        if (def.serverCharge()) {
            driveServerCharged(player, key, slot, def, input);
            return;
        }
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

    /**
     * Input for an ability whose charge the <b>server</b> times. The press opens the attempt through the
     * ordinary use payload and the release sends {@link ApparitionChargeReleaseC2SPayload}; between the two
     * this client reports nothing, because a client that could report its own release tick could always claim
     * a perfect one.
     *
     * <p>The local {@link ClientAbilityChargeState} still runs, but only to keep the existing charge-up ring
     * drawing. It is a preview: if it disagrees with the server's clock by a tick, the server is right.
     */
    private static void driveServerCharged(LocalPlayer player, KeyMapping key, int slot,
                                           AbilityDefinition def, AbilityInput input) {
        // A charge the server began without this client holding anything: one press commits it. Checked
        // before the hold logic so the press cannot also be read as the start of a second charge, and on
        // the click edge rather than isDown() so the keypress that opened the selector cannot commit the
        // charge it just created the instant the screen closes.
        if (adoptedServerCharge) {
            if (key.consumeClick()) {
                ClientPacketDistributor.sendToServer(ApparitionChargeReleaseC2SPayload.INSTANCE);
                adoptedServerCharge = false;
            }
            drain(key);
            return;
        }
        boolean down = !key.isUnbound() && key.isDown();
        if (down) {
            if (!charging || chargingSlot != slot) {
                charging = true;
                chargingServerSide = true;
                chargingSlot = slot;
                send(slot, pick(player, input));
            }
            // No ClientAbilityChargeState here. It exists to time a client-side charge and to hold the
            // target picked at the end of one, and a server-charged ability does neither: the server runs
            // the clock, and the release payload carries no target because the server re-runs the raycast
            // itself. Driving it was bookkeeping nothing read — its own javadoc claimed it kept a charge-up
            // ring drawing, but no such ring exists; Apparition's readout is the destination ring, which
            // ApparitionClientController draws straight from the server's phase packets.
        } else if (charging && chargingSlot == slot) {
            // A release is a commitment and is judged against the window, so it must not go out through
            // cancelCharge's withdrawal path.
            ClientPacketDistributor.sendToServer(ApparitionChargeReleaseC2SPayload.INSTANCE);
            chargingServerSide = false;
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

    /**
     * Drops the charge locally, and withdraws it on the server when the server was the one timing it.
     *
     * <p>The withdrawal is the whole point. Opening a screen or having the armed ability change used to clear
     * client state and say nothing, leaving the server holding an attempt nobody would ever release — which
     * then discharged into a catastrophic splinch out of a clear sky. An abort costs nothing, which is
     * correct: the player did not let go, they were interrupted.
     */
    private static void cancelCharge() {
        // Deliberately blind to adoptedServerCharge. This runs every tick a screen is open, and the
        // selector that begins an anchored charge is a screen — withdrawing here would abort the jump on
        // the frame it was chosen. An adopted charge that is genuinely abandoned collapses harmlessly on
        // its own; see ApparitionServerLogic#collapseAttempt.
        if (chargingServerSide && charging) {
            ClientPacketDistributor.sendToServer(ApparitionChargeAbortC2SPayload.INSTANCE);
        }
        charging = false;
        chargingServerSide = false;
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
