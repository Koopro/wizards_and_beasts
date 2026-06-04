package at.koopro.wizardsandbeasts.memory;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

public final class MemoryService {

    public static final float MEMORY_BONUS_SCALE = 6.0f;
    public static final float MAX_MEMORY_BONUS   = 20.0f;

    private MemoryService() {}

    /**
     * Forms a memory keyed by sourceKey unless that key is on cooldown.
     * Returns true if a memory was actually formed. All timing via level.getGameTime() (monotonic).
     */
    public static boolean tryFormMemory(ServerPlayer player, MemoryType type, float intensity,
                                        String sourceKey, long cooldownTicks) {
        PlayerMemoryData data = player.getData(ModAttachments.MEMORIES.get());
        long now = player.level().getGameTime();
        if (data.isOnCooldown(sourceKey, now)) return false;

        float clamped = Math.max(0f, Math.min(1f, intensity));
        data.addMemory(new MemoryEntry(type, clamped, sourceKey, now));
        data.setCooldown(sourceKey, now + cooldownTicks);
        return true;
    }

    /** Bounded, intensity-weighted Patronus contribution from accumulated HAPPY memories. */
    public static float patronusMemoryBonus(ServerPlayer player) {
        float sum = player.getData(ModAttachments.MEMORIES.get()).happyIntensitySum();
        return Math.min(MAX_MEMORY_BONUS, sum * MEMORY_BONUS_SCALE);
    }
}
