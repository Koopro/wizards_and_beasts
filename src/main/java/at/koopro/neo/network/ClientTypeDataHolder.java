package at.koopro.neo.network;

import at.koopro.neo.data.PlayerTypeData;
import net.minecraft.nbt.CompoundTag;

public class ClientTypeDataHolder {

    private static final PlayerTypeData INSTANCE = new PlayerTypeData();

    public static void load(CompoundTag tag) {
        INSTANCE.load(tag);
    }

    public static PlayerTypeData get() {
        return INSTANCE;
    }

    public static void clear() {
        INSTANCE.reset();
    }

    private ClientTypeDataHolder() {}
}
