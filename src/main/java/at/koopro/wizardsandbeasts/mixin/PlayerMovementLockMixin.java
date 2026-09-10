package at.koopro.wizardsandbeasts.mixin;

import at.koopro.wizardsandbeasts.movement.MovementLock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hangs the mod's movement locks — petrification, a Stupefy stun — on vanilla's own idea
 * of a body that cannot act.
 *
 * <h2>Why a mixin</h2>
 *
 * <p>NeoForge 21.11 has no movement hook. {@code LivingEvent} carries only {@code
 * LivingJumpEvent} and {@code LivingVisibilityEvent}; there is no event on {@code travel},
 * none on {@code isImmobile}, and {@code MovementInputUpdateEvent} is client-only and
 * fires <em>before</em> {@code LivingEntity.applyInput()} writes {@code xxa}/{@code zza}
 * (the trap {@code ClientInputAccessor} already documents). Holding a player still from an
 * event therefore means correcting them after the fact, on the server, every tick — which
 * is exactly the fight this replaces.
 *
 * <h2>Why these two points</h2>
 *
 * <p>{@code isImmobile()} has one caller, and it is the right one. {@code
 * LivingEntity.aiStep} runs it immediately after {@code applyInput()} and immediately
 * before the jump block and {@code travel}:
 *
 * <pre>{@code
 * this.applyInput();
 * if (this.isImmobile()) {
 *     this.jumping = false;
 *     this.xxa = 0.0F;
 *     this.zza = 0.0F;
 * }
 * }</pre>
 *
 * <p>That is input, steering and jump gone in one place, on <em>both</em> sides, at the
 * same point in the tick. Because the client reaches the same conclusion as the server,
 * its prediction never diverges and there is nothing to snap back — no {@code teleportTo},
 * no {@code hurtMarked}, no position packet per tick.
 *
 * <p>{@code travel} is the second half, and only for {@code PINNED}. {@code isImmobile}
 * unplugs the controls but leaves the body in physics: gravity still pulls, knockback
 * still shoves. That is correct for a stun and wrong for a statue, so petrification also
 * cancels the movement step outright.
 *
 * <h2>If this silently stops firing</h2>
 *
 * <p>It cannot silently: the config sets {@code injectors.defaultRequire: 1}, so an
 * injection point that no longer resolves is a crash at load rather than a stun that
 * quietly does nothing. If the <em>logic</em> is bypassed, petrified and stunned players
 * simply walk away; nothing else regresses.
 */
@Mixin(Player.class)
public abstract class PlayerMovementLockMixin {

    /**
     * Adds the mod's locks to whatever vanilla already decided (death, sleeping), rather
     * than replacing that answer — a sleeping player stays immobile whether or not the mod
     * has an opinion about them.
     */
    @Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
    private void wandb$applyMovementLock(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && MovementLock.isLocked((Player) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Takes gravity and knockback as well, for locks that mean the body is furniture.
     *
     * <p>The delta is zeroed rather than merely left alone: cancelling {@code travel}
     * stops motion being <em>applied</em>, but an impulse already written by a hit would
     * otherwise sit on the entity waiting for the lock to lift and then fire all at once.
     */
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void wandb$pinInPlace(Vec3 movement, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (MovementLock.isPinned(self)) {
            self.setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
