package at.koopro.neo.network;

import at.koopro.neo.data.PlayerVaultData;
import net.minecraft.nbt.CompoundTag;

public final class ClientVaultDataHolder {

    private static final PlayerVaultData INSTANCE = new PlayerVaultData();

    private ClientVaultDataHolder() {
    }

    public static PlayerVaultData get() {
        return INSTANCE;
    }

    public static void load(long knuts, long sickles, long galleons) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Knuts", knuts);
        tag.putLong("Sickles", sickles);
        tag.putLong("Galleons", galleons);
        INSTANCE.resetAll();
        INSTANCE.load(tag);
    }
}
