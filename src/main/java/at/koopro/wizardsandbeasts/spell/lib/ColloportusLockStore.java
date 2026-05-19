package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * API for Colloportus magical seals. Backed by per-dimension {@link ColloportusLockSavedData}.
 */
public final class ColloportusLockStore {

    private ColloportusLockStore() {}

    private static ColloportusLockSavedData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ColloportusLockSavedData.TYPE);
    }

    public static boolean isLocked(ServerLevel level, BlockPos pos) {
        return data(level).isLocked(pos);
    }

    public static void lock(ServerLevel level, BlockPos pos) {
        data(level).lock(pos);
    }

    public static void unlock(ServerLevel level, BlockPos pos) {
        data(level).remove(pos);
    }
}
