package at.koopro.wizardsandbeasts.skill.data;

import net.minecraft.nbt.CompoundTag;

public final class PlayerSkillDataMigrator {
    private PlayerSkillDataMigrator() {}

    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerSkillData.VERSION_KEY).orElse(0);
        if (version < 1) {
            migrateV0ToV1(tag);
        }
        tag.putInt(PlayerSkillData.VERSION_KEY, PlayerSkillData.CURRENT_VERSION);
    }

    private static void migrateV0ToV1(CompoundTag tag) {
        // Initial version stamp migration; field layout remains unchanged.
    }
}
