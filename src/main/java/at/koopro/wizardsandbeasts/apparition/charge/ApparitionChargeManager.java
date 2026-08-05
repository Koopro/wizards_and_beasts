package at.koopro.wizardsandbeasts.apparition.charge;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AbilityProficiency;
import at.koopro.wizardsandbeasts.apparition.ApparitionBroadcast;
import at.koopro.wizardsandbeasts.apparition.ApparitionPhase;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative Three Ds. Owns the tick counter, the window and the release evaluation for every
 * in-flight attempt.
 *
 * <p>The client sends <i>intent</i> — "I have begun", "I have let go" — and never a timing result. Letting
 * the client report its own release tick would let a player always release perfectly and never splinch,
 * which is the whole design. The framework's {@code AbilityInput.chargeTicks} stays what it is (a
 * client-side input contract, un-revalidated) and every other ability keeps riding it unchanged.
 *
 * <p>State is transient and per-player, cleaned up on logout by {@link PlayerScopedState}.
 */
@NullMarked
public final class ApparitionChargeManager {

    /** Vanilla's sneak factor; moving faster than this fraction of your walk speed counts as moving. */
    private static final double SNEAK_SPEED_FACTOR = 0.3;
    /** Inventory fullness above which the load itself destabilises the jump. */
    private static final double ENCUMBERED_FRACTION = 0.8;

    private static final PlayerScopedState<ApparitionCharge> CHARGES =
            PlayerScopedState.create("apparition_charge");

    private ApparitionChargeManager() {}

    /**
     * Begins an attempt, replacing any already in flight.
     *
     * @param anchor the memorised destination for {@link ApparitionTier#ANCHORED}, {@code null} for a blink
     * @return false when the player may not Apparate at all, in which case nothing was started
     */
    public static boolean begin(ServerPlayer player, ApparitionTier tier, @Nullable ApparitionPoint anchor) {
        // An attempt already in flight is never replaced. This is what lets an anchored jump be started from
        // the destination selector and then released with the same key press that would otherwise start a
        // blink: the press finds a charge already running and does nothing.
        if (isCharging(player)) {
            return false;
        }
        if (!ApparitionServerLogic.canBeginAttempt(player)) {
            return false;
        }
        float proficiency = AbilityProficiency.get(player, AbilityIds.APPARITION);
        ApparitionCharge charge = new ApparitionCharge(tier, proficiency,
                ApparitionServerLogic.windowFloorTicks(player), anchor);
        CHARGES.put(player, charge);
        ApparitionBroadcast.get().onChargeBegin(player, tier, charge.phase(),
                charge.windowOpen(), charge.windowClose());
        return true;
    }

    public static boolean isCharging(ServerPlayer player) {
        return CHARGES.contains(player.getUUID());
    }

    public static ApparitionPhase phase(ServerPlayer player) {
        ApparitionCharge charge = CHARGES.get(player);
        return charge == null ? ApparitionPhase.IDLE : charge.phase();
    }

    /** Drops the attempt with no cooldown, no exhaustion and no splinch. */
    public static void abort(ServerPlayer player) {
        ApparitionCharge charge = CHARGES.remove(player);
        if (charge != null) {
            // IDLE tells the presentation layer to tear down its motes; without it they would hang until the
            // client happened to receive something else about this player.
            ApparitionBroadcast.get().onPhaseChange(player, charge.tier(), ApparitionPhase.IDLE,
                    charge.elapsed(), charge.windowOpen(), charge.windowClose());
        }
    }

    /**
     * One tick of an in-flight attempt: re-aims, then discharges if the window has closed unreleased.
     * Called from the player tick.
     */
    public static void tick(ServerPlayer player) {
        ApparitionCharge charge = CHARGES.get(player);
        if (charge == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            abort(player);
            return;
        }
        ApparitionPhase before = charge.phase();
        charge.advance();
        if (charge.tier() == ApparitionTier.BLINK) {
            charge.setDestination(aim(level, player));
        }
        if (charge.isOverdue()) {
            resolve(player, charge, ApparitionWindow.FORCED_DISCHARGE);
            return;
        }

        ApparitionPhase after = charge.phase();
        if (after != before) {
            ApparitionBroadcast.get().onPhaseChange(player, charge.tier(), after,
                    charge.elapsed(), charge.windowOpen(), charge.windowClose());
        } else {
            ApparitionBroadcast.get().onChargeTick(player, charge.tier(), after,
                    charge.elapsed(), charge.windowOpen(), charge.windowClose());
        }
    }

    /** The player let go. Evaluates the window and resolves the attempt. */
    public static void release(ServerPlayer player) {
        ApparitionCharge charge = CHARGES.get(player);
        if (charge == null) {
            return;
        }
        resolve(player, charge, charge.missTicksOnRelease());
    }

    /**
     * Registers a hit taken mid-attempt. For {@link ApparitionTier#ANCHORED} this ends the attempt then and
     * there — a seventy-tick hold cannot survive being shot at, which is what makes it legal in combat and
     * impossible in one.
     *
     * <p>The abort resolves at a raw miss of zero: the wizard let go the instant they were struck, and it is
     * the hit's own {@code +4} inflation that lands them on the minor rung. Resolving at the real release
     * miss instead would read the interruption as sixty ticks of panic and tear them apart for being shot at
     * early, which is not what a flinch costs.
     */
    public static void onDamaged(ServerPlayer player) {
        ApparitionCharge charge = CHARGES.get(player);
        if (charge == null) {
            return;
        }
        charge.recordDamage();
        if (charge.tier().abortsOnDamage()) {
            resolve(player, charge, 0);
        }
    }

    private static void resolve(ServerPlayer player, ApparitionCharge charge, int missTicks) {
        CHARGES.remove(player);
        ApparitionServerLogic.completeAttempt(player, charge, missTicks, destabilization(player, charge));
    }

    /** Raycast from the eye, resolved to a spot the player fits in; {@code null} while the aim is invalid. */
    private static @Nullable Vec3 aim(ServerLevel level, ServerPlayer player) {
        float proficiency = AbilityProficiency.get(player, AbilityIds.APPARITION);
        double range = ApparitionTier.BLINK.rangeBlocks(proficiency);
        HitResult hit = player.pick(range, 0.0f, false);
        Vec3 aimed = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()))
                : player.getEyePosition().add(player.getLookAngle().scale(range));
        return ApparitionLanding.resolve(level, player, aimed);
    }

    /** Snapshot of everything working against the player right now. */
    private static Destabilization destabilization(ServerPlayer player, ApparitionCharge charge) {
        return new Destabilization(
                charge.damageInstances(),
                isMovingFasterThanSneak(player),
                player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA),
                isEncumbered(player),
                false,
                ApparitionServerLogic.isLicensed(player));
    }

    private static boolean isMovingFasterThanSneak(ServerPlayer player) {
        double sneakSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * SNEAK_SPEED_FACTOR;
        return player.getDeltaMovement().horizontalDistance() > sneakSpeed;
    }

    /** True when the pack is more than {@link #ENCUMBERED_FRACTION} full — a wizard laden is a wizard torn. */
    private static boolean isEncumbered(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        if (size <= 0) {
            return false;
        }
        int used = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used > size * ENCUMBERED_FRACTION;
    }

    /** Where the player is standing — the origin an attempt drops its splinched remains at. */
    public static BlockPos origin(ServerPlayer player) {
        return player.blockPosition();
    }
}
