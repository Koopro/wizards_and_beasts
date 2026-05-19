package at.koopro.wizardsandbeasts.currency.vault;

import net.minecraft.nbt.CompoundTag;

public final class PlayerVaultDataMigrator {
    private PlayerVaultDataMigrator() {}

    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerVaultData.VERSION_KEY).orElse(0);
        if (version < 1) {
            migrateV0ToV1(tag);
        }
        tag.putInt(PlayerVaultData.VERSION_KEY, PlayerVaultData.CURRENT_VERSION);
    }

    private static void migrateV0ToV1(CompoundTag tag) {
        // Initial version stamp migration; field layout remains unchanged.
    }
}
