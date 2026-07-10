package at.koopro.wizardsandbeasts.skill.data;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class PlayerSkillData implements ModAttachments.NbtSerializable {
    public static final String VERSION_KEY = "DataVersion";
    /**
     * v2 = web rework (adjacency allocation, 60-point cap, full refund);
     * v3 = wizard web content pass (geometry + fillers reshaped the graph → full refund again).
     * Any bump re-runs {@link #applyWebMigration} at login.
     */
    public static final int CURRENT_VERSION = 3;

    /**
     * Schema version of the data this instance was loaded from. Fresh instances (new players)
     * start at {@link #CURRENT_VERSION}; loading pre-rework NBT leaves this at the stored value
     * until the login-time web migration runs and stamps it forward.
     */
    private int dataVersion = CURRENT_VERSION;
    private int skillPoints;
    private int totalPointsEarned;
    private final Map<String, Integer> unlockedSkills = new LinkedHashMap<>();

    // NEW FIELDs — OWL progression counters. Incremented by game events; read by OWLGradeCalculator.
    private int metamorphFormsUsed;   // TRANSFIGURATION: distinct metamorphmagus disguise uses
    private int potionBrewPoints;     // POTIONS: complexity-weighted sum of successful brews
    private int plantsHarvested;      // HERBOLOGY: unique magical plants harvested
    private int arithmancyInteractions; // ARITHMANCY: rune/puzzle interactions in worldgen structures
    private int runicInteractions;    // ANCIENT_RUNES: runic item interactions
    private int divinationEvents;     // DIVINATION: correct moon predictions + crystal ball uses
    // HISTORY_OF_MAGIC: ids of lore tomes/plaques the player has studied. A Set (not a counter) so
    // re-reading the same book can't farm KNOWLEDGE or the History of Magic OWL grade — each unique
    // source counts exactly once. getLoreItemsRead() exposes the size for the existing readers.
    private final Set<String> loreEntriesRead = new LinkedHashSet<>();
    // One-shot milestone ledger: names of MilestoneType achievements already granted, so each
    // first-time stat bump fires exactly once even though the gameplay trigger may recur.
    private final Set<String> milestonesAchieved = new LinkedHashSet<>();
    private int astronomyEvents;      // ASTRONOMY: nights observing + moon phase tracking events
    private int muggleItems;          // MUGGLE_STUDIES: muggle items crafted or traded

    public int getSkillPoints() {
        return skillPoints;
    }

    /**
     * Adds earned points, clamped so {@code totalPointsEarned} never exceeds
     * {@link SkillSystemAPI#MAX_SKILL_POINTS}. No-op once the cap is reached.
     */
    public void addSkillPoints(int amount) {
        int grant = Math.min(amount, Math.max(0, SkillSystemAPI.MAX_SKILL_POINTS - totalPointsEarned));
        if (grant <= 0) {
            return;
        }
        skillPoints += grant;
        totalPointsEarned += grant;
    }

    // ── Web migration (schema v1 → v2) ──

    /** True if this data predates the current web schema and still needs the one-time full refund. */
    public boolean needsWebMigration() {
        return dataVersion < CURRENT_VERSION;
    }

    /**
     * One-time web rework migration: clears every allocation, refunds unconditionally by setting
     * unspent = earned, and clamps earned to {@link SkillSystemAPI#MAX_SKILL_POINTS} (pre-cap
     * players may exceed it). Stamps the schema version so relogs never re-fire.
     *
     * @return the number of cleared allocation entries
     */
    public int applyWebMigration() {
        int cleared = unlockedSkills.size();
        unlockedSkills.clear();
        totalPointsEarned = Math.min(totalPointsEarned, SkillSystemAPI.MAX_SKILL_POINTS);
        skillPoints = totalPointsEarned;
        dataVersion = CURRENT_VERSION;
        return cleared;
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

    public int getMetamorphFormsUsed() { return metamorphFormsUsed; }
    public void incrementMetamorphFormsUsed() { metamorphFormsUsed = Math.max(0, metamorphFormsUsed + 1); }
    public void setMetamorphFormsUsed(int v) { metamorphFormsUsed = Math.max(0, v); }

    public int getPotionBrewPoints() { return potionBrewPoints; }
    public void addPotionBrewPoints(int amount) { potionBrewPoints = Math.max(0, potionBrewPoints + amount); }
    public void setPotionBrewPoints(int v) { potionBrewPoints = Math.max(0, v); }

    public int getPlantsHarvested() { return plantsHarvested; }
    public void incrementPlantsHarvested() { plantsHarvested = Math.max(0, plantsHarvested + 1); }
    public void setPlantsHarvested(int v) { plantsHarvested = Math.max(0, v); }

    public int getArithmancyInteractions() { return arithmancyInteractions; }
    public void incrementArithmancyInteractions() { arithmancyInteractions = Math.max(0, arithmancyInteractions + 1); }
    public void setArithmancyInteractions(int v) { arithmancyInteractions = Math.max(0, v); }

    public int getRunicInteractions() { return runicInteractions; }
    public void incrementRunicInteractions() { runicInteractions = Math.max(0, runicInteractions + 1); }
    public void setRunicInteractions(int v) { runicInteractions = Math.max(0, v); }

    public int getDivinationEvents() { return divinationEvents; }
    public void incrementDivinationEvents() { divinationEvents = Math.max(0, divinationEvents + 1); }
    public void setDivinationEvents(int v) { divinationEvents = Math.max(0, v); }

    /** Number of distinct lore tomes/plaques studied — feeds KNOWLEDGE and the History of Magic OWL grade. */
    public int getLoreItemsRead() { return loreEntriesRead.size(); }
    /** True if this lore source has already been studied (no further credit on re-read). */
    public boolean hasReadLoreEntry(String loreId) { return loreEntriesRead.contains(loreId); }
    /** Records a lore source as studied; returns true only the first time (i.e. when it actually counts). */
    public boolean markLoreEntryRead(String loreId) {
        return loreId != null && !loreId.isBlank() && loreEntriesRead.add(loreId);
    }

    /** True if this milestone has already been granted (one-shot guard). */
    public boolean hasAchievedMilestone(String milestoneId) { return milestonesAchieved.contains(milestoneId); }
    /** Records a milestone as achieved; returns true only the first time (i.e. when the bump should fire). */
    public boolean markMilestoneAchieved(String milestoneId) {
        return milestoneId != null && !milestoneId.isBlank() && milestonesAchieved.add(milestoneId);
    }

    public int getAstronomyEvents() { return astronomyEvents; }
    public void incrementAstronomyEvents() { astronomyEvents = Math.max(0, astronomyEvents + 1); }
    public void setAstronomyEvents(int v) { astronomyEvents = Math.max(0, v); }

    public int getMuggleItems() { return muggleItems; }
    public void incrementMuggleItems() { muggleItems = Math.max(0, muggleItems + 1); }
    public void setMuggleItems(int v) { muggleItems = Math.max(0, v); }

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
        // The instance's version, NOT the constant: pre-migration data must keep its old stamp so
        // the login-time web migration still fires after an intermediate save.
        tag.putInt(VERSION_KEY, dataVersion);
        tag.putInt("SkillPoints", skillPoints);
        tag.putInt("TotalPointsEarned", totalPointsEarned);
        NbtHelper.saveStringIntMap(tag, "UnlockedSkills", unlockedSkills);
        tag.putInt("MetamorphFormsUsed", metamorphFormsUsed);
        tag.putInt("PotionBrewPoints", potionBrewPoints);
        tag.putInt("PlantsHarvested", plantsHarvested);
        tag.putInt("ArithmancyInteractions", arithmancyInteractions);
        tag.putInt("RunicInteractions", runicInteractions);
        tag.putInt("DivinationEvents", divinationEvents);
        NbtHelper.saveStringSet(tag, "LoreEntriesRead", loreEntriesRead);
        NbtHelper.saveStringSet(tag, "MilestonesAchieved", milestonesAchieved);
        tag.putInt("AstronomyEvents", astronomyEvents);
        tag.putInt("MuggleItems", muggleItems);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        PlayerSkillDataMigrator.migrate(tag);
        dataVersion = tag.getInt(VERSION_KEY).orElse(CURRENT_VERSION);
        skillPoints = tag.getInt("SkillPoints").orElse(0);
        totalPointsEarned = tag.getInt("TotalPointsEarned").orElse(0);
        unlockedSkills.clear();
        unlockedSkills.putAll(NbtHelper.loadStringIntMap(tag, "UnlockedSkills"));
        metamorphFormsUsed = tag.getInt("MetamorphFormsUsed").orElse(0);
        potionBrewPoints = tag.getInt("PotionBrewPoints").orElse(0);
        plantsHarvested = tag.getInt("PlantsHarvested").orElse(0);
        arithmancyInteractions = tag.getInt("ArithmancyInteractions").orElse(0);
        runicInteractions = tag.getInt("RunicInteractions").orElse(0);
        divinationEvents = tag.getInt("DivinationEvents").orElse(0);
        loreEntriesRead.clear();
        loreEntriesRead.addAll(NbtHelper.loadStringSet(tag, "LoreEntriesRead"));
        milestonesAchieved.clear();
        milestonesAchieved.addAll(NbtHelper.loadStringSet(tag, "MilestonesAchieved"));
        astronomyEvents = tag.getInt("AstronomyEvents").orElse(0);
        muggleItems = tag.getInt("MuggleItems").orElse(0);
    }

    public PlayerSkillData copy() {
        PlayerSkillData copy = new PlayerSkillData();
        CompoundTag tag = this.save();
        copy.load(tag);
        return copy;
    }
}
