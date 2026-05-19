package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellDataMigrator;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link PlayerSpellDataMigrator} chain.
 * The contract: <em>any</em> tag that ever lived through {@link PlayerSpellData#load(CompoundTag)}
 * must end up at {@link PlayerSpellData#CURRENT_VERSION}, and pre-existing fields must survive.
 */
class PlayerSpellDataMigratorTest {

    @Test
    void migrate_unversionedTag_treatedAsV0AndUpgraded() {
        CompoundTag legacy = new CompoundTag();
        // Legacy v0 had no DataVersion key; same field layout otherwise.
        legacy.putInt("ActiveSlot", 2);

        PlayerSpellDataMigrator.migrate(legacy);

        assertEquals(PlayerSpellData.CURRENT_VERSION,
                legacy.getInt(PlayerSpellData.VERSION_KEY).orElse(-1),
                "Migrator must stamp the current version on legacy tags.");
        assertEquals(2, legacy.getInt("ActiveSlot").orElse(0),
                "v0 -> v1 migration must preserve existing ActiveSlot.");
    }

    @Test
    void migrate_currentVersion_isNoOp() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PlayerSpellData.VERSION_KEY, PlayerSpellData.CURRENT_VERSION);
        tag.putInt("ActiveSlot", 1);

        CompoundTag before = tag.copy();
        PlayerSpellDataMigrator.migrate(tag);

        assertEquals(before, tag, "Tag at current version must be unchanged by migrator.");
    }

    @Test
    void load_acceptsLegacyUnversionedSave() {
        // Simulate a save from before VERSION_KEY existed: write fields manually.
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("ActiveSlot", 3);
        // Intentionally NO DataVersion key.

        PlayerSpellData data = new PlayerSpellData();
        data.load(legacy);

        assertEquals(3, data.getActiveSlot(),
                "Loading a legacy unversioned tag must still produce the right state.");
    }

    @Test
    void save_alwaysStampsCurrentVersion() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell("lumos");
        CompoundTag tag = data.save();
        assertTrue(tag.getInt(PlayerSpellData.VERSION_KEY).isPresent(),
                "save() must always write a DataVersion field.");
        assertEquals(PlayerSpellData.CURRENT_VERSION,
                tag.getInt(PlayerSpellData.VERSION_KEY).orElse(-1));
    }

    @Test
    void load_remapsLegacyNamespaceToCurrentModId() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("ActiveSlot", 0);
        legacy.putString("Slot0", "WizardsAndBeastsMod:lumos");
        CompoundTag castCount = new CompoundTag();
        castCount.putString("k", "WizardsAndBeastsMod:lumos");
        castCount.putInt("v", 3);
        net.minecraft.nbt.ListTag castList = new net.minecraft.nbt.ListTag();
        castList.add(castCount);
        legacy.put("CastCount", castList);
        legacy.putInt(PlayerSpellData.VERSION_KEY, PlayerSpellData.CURRENT_VERSION);

        PlayerSpellData data = new PlayerSpellData();
        data.load(legacy);

        assertEquals("wizards_and_beasts:lumos", data.getLoadoutSpell(0));
    }
}
