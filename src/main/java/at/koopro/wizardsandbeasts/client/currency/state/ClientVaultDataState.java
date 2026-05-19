package at.koopro.wizardsandbeasts.client.currency.state;

import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import net.minecraft.nbt.CompoundTag;

public final class ClientVaultDataState {
    private static final PlayerVaultData INSTANCE = new PlayerVaultData();
    private static int lastSyncVersion;

    private ClientVaultDataState() {}

    public static PlayerVaultData get() {
        return INSTANCE;
    }

    public static void load(int syncVersion, long knuts, long sickles, long galleons) {
        if (syncVersion < lastSyncVersion) {
            return;
        }
        lastSyncVersion = syncVersion;
        CompoundTag tag = new CompoundTag();
        tag.putLong("Knuts", knuts);
        tag.putLong("Sickles", sickles);
        tag.putLong("Galleons", galleons);
        INSTANCE.resetAll();
        INSTANCE.load(tag);
    }

    public static void load(long knuts, long sickles, long galleons) {
        load(lastSyncVersion + 1, knuts, sickles, galleons);
    }
}
