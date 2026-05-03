package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerTypeDataMigratorTest {

    @Test
    void load_unversionedTag_stampsCurrentVersion() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", WizType.WIZARDKIND.getId());
        tag.putString("Subtype", WizSubtype.PURE_BLOOD.getId());
        PlayerTypeData data = new PlayerTypeData();
        data.load(tag);

        assertEquals(WizType.WIZARDKIND, data.getSelectedType());
        assertEquals(WizSubtype.PURE_BLOOD, data.getSelectedSubtype());
        assertEquals(PlayerTypeData.CURRENT_VERSION, tag.getInt(PlayerTypeData.VERSION_KEY).orElse(0));
    }
}
