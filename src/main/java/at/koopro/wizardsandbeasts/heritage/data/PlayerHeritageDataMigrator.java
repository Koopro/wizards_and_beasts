package at.koopro.wizardsandbeasts.heritage.data;

import net.minecraft.nbt.CompoundTag;

public final class PlayerHeritageDataMigrator {
    private PlayerHeritageDataMigrator() {}

    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerHeritageData.VERSION_KEY).orElse(0);
        if (version >= PlayerHeritageData.CURRENT_VERSION) {
            return;
        }
        tag.putInt(PlayerHeritageData.VERSION_KEY, PlayerHeritageData.CURRENT_VERSION);
    }
}
