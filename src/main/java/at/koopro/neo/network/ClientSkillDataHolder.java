package at.koopro.neo.network;

import at.koopro.neo.data.PlayerSkillData;
import net.minecraft.nbt.CompoundTag;

/**
 * Client-side cache of the local player's skill data, synced from server.
 */
public final class ClientSkillDataHolder {

    private static final PlayerSkillData INSTANCE = new PlayerSkillData();

    private ClientSkillDataHolder() {}

    public static PlayerSkillData get() {
        return INSTANCE;
    }

    public static void load(CompoundTag tag) {
        INSTANCE.resetAll();
        INSTANCE.load(tag);
    }
}
