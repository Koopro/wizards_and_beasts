package at.koopro.wizardsandbeasts.skill.data;

import net.minecraft.nbt.CompoundTag;

/**
 * NBT-level structural migrations for {@link PlayerSkillData}. Only stamps tags up to the highest
 * version whose migration is purely structural (v1). The v1 → v2 web rework migration is
 * behavioral (full refund + cap clamp, needs the player for logging/resync) and runs at login via
 * {@link PlayerSkillData#applyWebMigration()} — this class must NOT stamp v2, or that migration
 * would be skipped.
 */
public final class PlayerSkillDataMigrator {
    /** Highest version this NBT-level migrator produces. */
    private static final int STRUCTURAL_VERSION = 1;

    private PlayerSkillDataMigrator() {}

    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerSkillData.VERSION_KEY).orElse(0);
        if (version < 1) {
            migrateV0ToV1(tag);
        }
        if (version < STRUCTURAL_VERSION) {
            tag.putInt(PlayerSkillData.VERSION_KEY, STRUCTURAL_VERSION);
        }
    }

    private static void migrateV0ToV1(CompoundTag tag) {
        // Initial version stamp migration; field layout remains unchanged.
    }
}
