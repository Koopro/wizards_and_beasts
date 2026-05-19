package at.koopro.wizardsandbeasts.spell.data;

import at.koopro.wizardsandbeasts.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/**
 * Centralized linear migration chain for {@link PlayerSpellData} on disk.
 * <p>
 * Each {@code migrateXtoY} method takes a tag at version {@code X} and rewrites
 * it in place to version {@code Y}. {@link #migrate(CompoundTag)} chains them so
 * the load path stays linear and version-jumps cannot silently skip a step.
 * <p>
 * When you bump {@link PlayerSpellData#CURRENT_VERSION}, add a new
 * {@code migrate(N-1)to(N)} method here and call it from {@link #migrate}.
 */
public final class PlayerSpellDataMigrator {

    private PlayerSpellDataMigrator() {}

    /**
     * Migrates {@code tag} forward to {@link PlayerSpellData#CURRENT_VERSION}
     * in place. Safe to call on tags that are already current (no-op) or on
     * unversioned legacy tags (treated as v0).
     */
    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerSpellData.VERSION_KEY).orElse(0);

        if (version < 1) {
            migrateV0toV1(tag);
            version = 1;
        }

        if (version < 2) {
            migrateV1toV2(tag);
            version = 2;
        }

        tag.putInt(PlayerSpellData.VERSION_KEY, PlayerSpellData.CURRENT_VERSION);
    }

    /**
     * v0 = legacy unversioned tag (the schema in use prior to introducing
     * {@link PlayerSpellData#VERSION_KEY}). v1 has the same field layout, so
     * this is a no-op apart from the version stamp written by {@link #migrate}.
     * Kept as an explicit step so the chain is auditable and future steps can
     * assume v1+ semantics.
     */
    private static void migrateV0toV1(CompoundTag tag) {
        // No field changes between v0 and v1; the introduction of VERSION_KEY
        // is the migration. Intentionally empty.
    }

    /**
     * Introduces SuccessfulHits as the source of proficiency progression.
     * Existing saves are migrated once with a lossy but fair baseline:
     * successfulHits = castCount * 0.6 (rounded).
     */
    private static void migrateV1toV2(CompoundTag tag) {
        if (tag.contains("SuccessfulHits")) {
            return;
        }
        Map<String, Integer> castCounts = NbtHelper.loadStringIntMap(tag, "CastCount");
        Map<String, Integer> successfulHits = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> entry : castCounts.entrySet()) {
            int migrated = Math.max(0, Math.round(entry.getValue() * 0.6f));
            if (migrated > 0) {
                successfulHits.put(entry.getKey(), migrated);
            }
        }
        NbtHelper.saveStringIntMap(tag, "SuccessfulHits", successfulHits);
    }
}
