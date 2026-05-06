package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.type.HeritageVariant;
import at.koopro.wizardsandbeasts.type.Heritage;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
