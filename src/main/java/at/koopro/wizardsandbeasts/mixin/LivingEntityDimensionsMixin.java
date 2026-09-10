package at.koopro.wizardsandbeasts.mixin;

import at.koopro.wizardsandbeasts.form.PlayerBoxOverrides;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the mod's player boxes the answer to every question about a player's size, not
 * just the cached one.
 *
 * <h2>Why a mixin</h2>
 *
 * <p>{@code EntityEvent.Size} — which is what the form and flight boxes used to hang on —
 * fires in exactly one place, {@code Entity.refreshDimensions()}, and its result lands in
 * the cached {@code dimensions} field. Vanilla does not route through that field when it
 * wants to know whether a shape would fit somewhere: it calls {@code getDimensions(pose)}
 * directly, from pose selection, the suffocation test, dismount placement and the
 * passenger attachment point. All of those were seeing the untouched {@code 0.6 x 1.8}
 * player box. There is no event on that method, and it is {@code final}, so there is
 * nothing to override either. See {@link PlayerBoxOverrides} for the specific breakages.
 *
 * <h2>Why {@code getDimensions} and not {@code Avatar.getDefaultDimensions}</h2>
 *
 * <p>{@code Avatar} is the narrower target and would look like the tidier one, but it
 * returns the box <em>before</em> {@code getDimensions} multiplies by {@code getScale()}.
 * The mod sets {@code Attributes.SCALE} in parallel (see {@code SizeSystemAPI}), so an
 * override there would have to be pre-divided by the scale for vanilla to multiply it back
 * — arithmetic with a wrong answer available and nothing gained. At {@code RETURN} here
 * the value in hand is already the finished one, identical to what the old listeners
 * received and replaced, so the move is provably behaviour-preserving. It also covers
 * {@code Pose.SLEEPING}, which {@code getDefaultDimensions} is short-circuited past.
 *
 * <p>{@code final} on the target stops Java overriding it. It does not stop an injection.
 *
 * <h2>Cost</h2>
 *
 * <p>This sits on {@code LivingEntity} because that is where the method is declared, so it
 * runs for every living entity. The {@code instanceof} is the first statement and is the
 * whole cost for everything that is not a player; {@code getDimensions} is called a
 * handful of times per entity per tick, not in an inner loop.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDimensionsMixin {

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void wandb$overridePlayerBox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        EntityDimensions override = PlayerBoxOverrides.resolve(player, cir.getReturnValue());
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
