package at.koopro.wizardsandbeasts.heritage.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Brings a saved heritage record up to {@link PlayerHeritageData#CURRENT_VERSION} before it is read.
 *
 * <h2>Version 2 — werewolf and obscurial became conditions (2026-09-17)</h2>
 * Werewolf and Obscurial were saved as heritages with their own "lineages" ({@code bitten}, {@code born},
 * {@code savage_bite}; {@code suppressed}, {@code unleashed}). They are conditions a Wizardkind character carries now,
 * so such a record becomes Wizardkind with the old lineage kept as the condition's origin. The character's real lineage
 * was never recorded — the heritage replaced it — so it is set to half-blood, the most common and the one that claims
 * least.
 */
public final class PlayerHeritageDataMigrator {

    static final String LEGACY_WEREWOLF = "werewolf";
    static final String LEGACY_OBSCURIAL = "obscurial";
    static final String DEFAULT_LINEAGE = "half_blood";

    private PlayerHeritageDataMigrator() {}

    public static void migrate(CompoundTag tag) {
        int version = tag.getInt(PlayerHeritageData.VERSION_KEY).orElse(0);
        if (version >= PlayerHeritageData.CURRENT_VERSION) {
            return;
        }
        if (version < 2) {
            String type = tag.getString("Type").orElse("");
            String condition = switch (type) {
                case LEGACY_WEREWOLF -> "lycanthropy";
                case LEGACY_OBSCURIAL -> "obscurus";
                default -> null;
            };
            if (condition != null) {
                tag.putString("Condition", condition);
                tag.getString("Subtype").ifPresent(origin -> tag.putString("ConditionOrigin", origin));
                tag.putString("Type", "wizardkind");
                tag.putString("Subtype", DEFAULT_LINEAGE);
            }
        }
        tag.putInt(PlayerHeritageData.VERSION_KEY, PlayerHeritageData.CURRENT_VERSION);
    }
}
