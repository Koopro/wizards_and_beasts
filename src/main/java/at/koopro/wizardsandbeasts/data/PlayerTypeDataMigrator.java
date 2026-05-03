package at.koopro.wizardsandbeasts.data;

import net.minecraft.nbt.CompoundTag;

public final class PlayerTypeDataMigrator {
    private PlayerTypeDataMigrator() {}

    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerTypeData.VERSION_KEY).orElse(0);
        if (version < 1) {
            migrateV0ToV1(tag);
        }
        tag.putInt(PlayerTypeData.VERSION_KEY, PlayerTypeData.CURRENT_VERSION);
    }

    private static void migrateV0ToV1(CompoundTag tag) {
        // Initial version stamp migration; field layout remains unchanged.
    }
}
