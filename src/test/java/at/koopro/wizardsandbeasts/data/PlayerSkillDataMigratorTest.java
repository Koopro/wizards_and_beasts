package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkillDataMigratorTest {

    @Test
    void load_unversionedTag_needsWebMigration() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SkillPoints", 4);
        tag.putInt("TotalPointsEarned", 9);
        PlayerSkillData data = new PlayerSkillData();
        data.load(tag);

        assertEquals(4, data.getSkillPoints());
        assertEquals(9, data.getTotalPointsEarned());
        // NBT migrator only stamps structural versions (v1); the behavioral web migration (v2)
        // must still be pending so the login handler fires it.
        assertEquals(1, tag.getInt(PlayerSkillData.VERSION_KEY).orElse(0));
        assertTrue(data.needsWebMigration());
    }

    @Test
    void freshData_isCurrentVersion_noMigration() {
        PlayerSkillData data = new PlayerSkillData();
        assertFalse(data.needsWebMigration());
    }

    @Test
    void v2Data_needsV3ContentMigration() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PlayerSkillData.VERSION_KEY, 2);
        tag.putInt("SkillPoints", 10);
        tag.putInt("TotalPointsEarned", 30);
        PlayerSkillData data = new PlayerSkillData();
        data.load(tag);
        data.setSkillLevel("basic_casting", 1);

        assertTrue(data.needsWebMigration(), "v2 saves must re-migrate for the Phase 4 graph reshape");
        data.applyWebMigration();
        assertTrue(data.getUnlockedSkills().isEmpty());
        assertEquals(30, data.getSkillPoints(), "under-cap earned refunds without clamping");
        assertFalse(data.needsWebMigration());
    }

    @Test
    void webMigration_refundsClears_andClampsEarnedToCap() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PlayerSkillData.VERSION_KEY, 1);
        tag.putInt("SkillPoints", 3);
        tag.putInt("TotalPointsEarned", 75); // pre-cap player above the new 60 cap
        PlayerSkillData data = new PlayerSkillData();
        data.load(tag);
        data.setSkillLevel("basic_casting", 1);
        data.setSkillLevel("stupefy_power", 2);

        assertTrue(data.needsWebMigration());
        int cleared = data.applyWebMigration();

        assertEquals(2, cleared);
        assertTrue(data.getUnlockedSkills().isEmpty());
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, data.getTotalPointsEarned(), "earned clamps to cap");
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, data.getSkillPoints(), "full refund: unspent == earned");
        assertFalse(data.needsWebMigration(), "version stamped forward");

        // Idempotent across save/load (relog): version travels with the tag.
        PlayerSkillData reloaded = new PlayerSkillData();
        reloaded.load(data.save());
        assertFalse(reloaded.needsWebMigration());
    }

    @Test
    void addSkillPoints_clampsAtCap() {
        PlayerSkillData data = new PlayerSkillData();
        data.addSkillPoints(SkillSystemAPI.MAX_SKILL_POINTS - 5);
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS - 5, data.getTotalPointsEarned());

        data.addSkillPoints(999); // grants only the 5 remaining
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, data.getTotalPointsEarned());
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, data.getSkillPoints());

        data.addSkillPoints(1); // at cap: no-op
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, data.getTotalPointsEarned());
        assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, data.getSkillPoints());
    }
}
