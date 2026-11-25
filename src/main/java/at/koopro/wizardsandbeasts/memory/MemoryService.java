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

    /** How much each unit of painful intensity takes from the Patronus, and the most it can take. */
    public static final float PAIN_SCALE       = 5.0f;
    public static final float MAX_PAIN_PENALTY = 15.0f;

    /**
     * What painful memories take from the Patronus (owner, A12 option 1): bounded, so the charm grows harder but never
     * impossible. Canon: the Dementors' pull is the memories a wizard would rather not keep.
     */
    public static float patronusMemoryPenalty(ServerPlayer player) {
        float sum = player.getData(ModAttachments.MEMORIES.get()).intensitySum(MemoryType.PAINFUL);
        return Math.min(MAX_PAIN_PENALTY, sum * PAIN_SCALE);
    }

    /** Takes the newest painful memory away (a Swooping Evil's sting); whether there was one. */
    public static boolean forgetNewestPainful(ServerPlayer player) {
        return player.getData(ModAttachments.MEMORIES.get()).forgetNewest(MemoryType.PAINFUL).isPresent();
    }

    /** Bounded, intensity-weighted Patronus contribution from accumulated HAPPY memories. */
    public static float patronusMemoryBonus(ServerPlayer player) {
        float sum = player.getData(ModAttachments.MEMORIES.get()).happyIntensitySum();
        return Math.min(MAX_MEMORY_BONUS, sum * MEMORY_BONUS_SCALE);
    }
}
