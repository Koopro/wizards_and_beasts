package at.koopro.neo.data;

import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.skill.Skill;
import at.koopro.neo.skill.SkillTrees;
import at.koopro.neo.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerSkillData implements ModAttachments.NbtSerializable {

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

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SkillPoints", skillPoints);
        tag.putInt("TotalPointsEarned", totalPointsEarned);
        NbtHelper.saveStringIntMap(tag, "UnlockedSkills", unlockedSkills);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
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
