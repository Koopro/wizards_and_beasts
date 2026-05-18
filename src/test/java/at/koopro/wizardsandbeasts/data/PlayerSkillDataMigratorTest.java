package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerSkillDataMigratorTest {

    @Test
    void load_unversionedTag_stampsCurrentVersion() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SkillPoints", 4);
        tag.putInt("TotalPointsEarned", 9);
        PlayerSkillData data = new PlayerSkillData();
        data.load(tag);

        assertEquals(4, data.getSkillPoints());
        assertEquals(9, data.getTotalPointsEarned());
        assertEquals(PlayerSkillData.CURRENT_VERSION, tag.getInt(PlayerSkillData.VERSION_KEY).orElse(0));
    }
}
