package at.koopro.wizardsandbeasts.client.ability.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Client-only view of the in-progress hold-to-charge trigger: which ability is charging, how far along, and
 * what it is currently aimed at. Written by {@code AbilityWheelController} and read by per-ability preview
 * renderers (the Apparition destination ring), so a charge affordance survives the move off dedicated
 * keybinds without each renderer owning its own input handling.
 */
@NullMarked
public final class ClientAbilityChargeState {

    @Nullable
    private static Identifier abilityId;
    private static int heldTicks;
    private static int chargeTicks;
    @Nullable
    private static Vec3 targetPosition;
    @Nullable
    private static BlockPos targetBlockPos;
    private static int targetEntityId;

    private ClientAbilityChargeState() {}

    public static void begin(Identifier ability, int requiredTicks) {
        abilityId = ability;
        chargeTicks = requiredTicks;
        heldTicks = 0;
        clearTarget();
    }

    public static void tick() {
        heldTicks++;
    }

    public static void setBlockTarget(BlockPos blockPos, Vec3 position) {
        targetBlockPos = blockPos;
        targetPosition = position;
        targetEntityId = 0;
    }

    public static void setEntityTarget(int entityId) {
        targetEntityId = entityId;
        targetBlockPos = null;
        targetPosition = null;
    }

    public static void clearTarget() {
        targetPosition = null;
        targetBlockPos = null;
        targetEntityId = 0;
    }

    public static void clear() {
        abilityId = null;
        heldTicks = 0;
        chargeTicks = 0;
        clearTarget();
    }

    @Nullable
    public static Identifier abilityId() {
        return abilityId;
    }

    public static boolean isCharging(Identifier ability) {
        return ability.equals(abilityId);
    }

    /** True once the hold has met the definition's charge window — the point the preview becomes live. */
    public static boolean isCharged() {
        return abilityId != null && heldTicks >= chargeTicks;
    }

    public static int heldTicks() {
        return heldTicks;
    }

    @Nullable
    public static Vec3 targetPosition() {
        return targetPosition;
    }

    @Nullable
    public static BlockPos targetBlockPos() {
        return targetBlockPos;
    }

    public static int targetEntityId() {
        return targetEntityId;
    }
}
