package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHeritageDataMigratorTest {

    @Test
    void load_unversionedTag_stampsCurrentVersion() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", Heritage.WIZARDKIND.getId());
        tag.putString("Subtype", HeritageVariant.PURE_BLOOD.getId());
        PlayerHeritageData data = new PlayerHeritageData();
        data.load(tag);

        assertEquals(Heritage.WIZARDKIND, data.getSelectedHeritage());
        assertEquals(HeritageVariant.PURE_BLOOD, data.getSelectedHeritageVariant());
        assertEquals(PlayerHeritageData.CURRENT_VERSION, tag.getInt(PlayerHeritageData.VERSION_KEY).orElse(0));
    }

    /**
     * A save written while Werewolf was a heritage. The character was a werewolf and, as far as the record went,
     * nothing else — so the migration has to give them back a lineage as well as the condition.
     */
    @Test
    void load_legacyWerewolfHeritage_becomesAWizardCarryingLycanthropy() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", "werewolf");
        tag.putString("Subtype", "savage_bite");
        PlayerHeritageData data = new PlayerHeritageData();
        data.load(tag);

        assertEquals(Heritage.WIZARDKIND, data.getSelectedHeritage());
        assertEquals(HeritageVariant.HALF_BLOOD, data.getSelectedHeritageVariant(),
                "the real lineage was never recorded, so the migration claims the least");
        assertEquals(ConditionOrigin.SAVAGE_BITTEN, data.getCondition());
        assertTrue(data.hasCondition(MagicalCondition.LYCANTHROPY));
        assertTrue(data.canUseWand(), "they were a wizard before the bite and still are");
    }

    @Test
    void load_legacyObscurialHeritage_becomesAWizardCarryingAnObscurus() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", "obscurial");
        tag.putString("Subtype", "unleashed");
        PlayerHeritageData data = new PlayerHeritageData();
        data.load(tag);

        assertEquals(Heritage.WIZARDKIND, data.getSelectedHeritage());
        assertEquals(ConditionOrigin.UNLEASHED, data.getCondition());
        assertFalse(data.canCast(), "the Obscurus still seals their magic after the migration");
    }

    @Test
    void load_ordinaryWizard_gainsNoCondition() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", "wizardkind");
        tag.putString("Subtype", "pure_blood");
        PlayerHeritageData data = new PlayerHeritageData();
        data.load(tag);

        assertNull(data.getCondition());
        assertEquals(HeritageVariant.PURE_BLOOD, data.getSelectedHeritageVariant());
    }

    /** A v2 record is already current: migrating it again must not re-read its lineage as a condition origin. */
    @Test
    void load_currentRecordWithACondition_isLeftAlone() {
        PlayerHeritageData saved = new PlayerHeritageData();
        saved.setSelectedHeritage(Heritage.WIZARDKIND);
        saved.setSelectedHeritageVariant(HeritageVariant.MUGGLE_BORN);
        saved.setCondition(ConditionOrigin.BITTEN);
        CompoundTag tag = saved.save();

        PlayerHeritageData loaded = new PlayerHeritageData();
        loaded.load(tag);

        assertEquals(HeritageVariant.MUGGLE_BORN, loaded.getSelectedHeritageVariant());
        assertEquals(ConditionOrigin.BITTEN, loaded.getCondition());
    }
}
