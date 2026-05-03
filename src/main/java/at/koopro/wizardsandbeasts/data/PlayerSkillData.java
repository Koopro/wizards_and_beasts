package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerSkillData implements ModAttachments.NbtSerializable {
    public static final String VERSION_KEY = "DataVersion";
    public static final int CURRENT_VERSION = 1;

    private int skillPoints;
    private int totalPointsEarned;
    private final Map<String, Integer> unlockedSkills = new LinkedHashMap<>();

    public int getSkillPoints() {
        return skillPoints;
    }

    public void addSkillPoints(int amount) {
        skillPoints += amount;
        totalPointsEarned += amount;
    }

    public boolean spendSkillPoints(int amount) {
        if (skillPoints < amount) return false;
        skillPoints -= amount;
        return true;
    }

    public void setSkillPoints(int amount) {
        skillPoints = Math.max(0, amount);
    }

    public int getTotalPointsEarned() {
        return totalPointsEarned;
    }

    public void setTotalPointsEarned(int amount) {
        totalPointsEarned = Math.max(0, amount);
    }

    public int getSkillLevel(String skillId) {
        return unlockedSkills.getOrDefault(skillId, 0);
    }

    public boolean hasSkill(String skillId) {
        return getSkillLevel(skillId) > 0;
    }

    public boolean isMaxed(String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) return false;
        return getSkillLevel(skillId) >= skill.getMaxLevel();
    }

    public void setSkillLevel(String skillId, int level) {
        if (level <= 0) {
            unlockedSkills.remove(skillId);
        } else {
            unlockedSkills.put(skillId, level);
        }
    }

    public void resetAll() {
        int refund = 0;
        for (Map.Entry<String, Integer> entry : unlockedSkills.entrySet()) {
            Skill skill = SkillTrees.byId(entry.getKey());
            if (skill != null) {
                refund += skill.getPointCost() * entry.getValue();
            }
        }
        unlockedSkills.clear();
        skillPoints += refund;
    }

    public void resetSkill(String skillId) {
        int level = getSkillLevel(skillId);
        if (level <= 0) return;
        Skill skill = SkillTrees.byId(skillId);
        if (skill != null) {
            skillPoints += skill.getPointCost() * level;
        }
        unlockedSkills.remove(skillId);
    }

    public Map<String, Integer> getUnlockedSkills() {
        return Collections.unmodifiableMap(unlockedSkills);
    }

    public void applySync(int points, int totalEarned, Map<String, Integer> skills) {
        skillPoints = Math.max(0, points);
        totalPointsEarned = Math.max(0, totalEarned);
        unlockedSkills.clear();
        if (skills == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : skills.entrySet()) {
            int level = Math.max(0, entry.getValue());
            if (level > 0) {
                unlockedSkills.put(entry.getKey(), level);
            }
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, CURRENT_VERSION);
        tag.putInt("SkillPoints", skillPoints);
        tag.putInt("TotalPointsEarned", totalPointsEarned);
        NbtHelper.saveStringIntMap(tag, "UnlockedSkills", unlockedSkills);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        PlayerSkillDataMigrator.migrate(tag);
        skillPoints = tag.getInt("SkillPoints").orElse(0);
        totalPointsEarned = tag.getInt("TotalPointsEarned").orElse(0);
        unlockedSkills.clear();
        unlockedSkills.putAll(NbtHelper.loadStringIntMap(tag, "UnlockedSkills"));
    }

    public PlayerSkillData copy() {
        PlayerSkillData copy = new PlayerSkillData();
        CompoundTag tag = this.save();
        copy.load(tag);
        return copy;
    }
}
