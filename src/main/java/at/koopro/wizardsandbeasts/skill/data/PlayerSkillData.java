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
     * v3 = wizard web content pass (geometry + fillers reshaped the graph → full refund again);
     * v4 = filler purge (70 nodes deleted, 30 rebuilt as pathways, 9 spell nodes added). A bump is
     * mandatory here rather than merely tidy: a saved allocation names node ids that no longer
     * exist, and {@code SkillTrees.byId} answers null for them — so those points would be neither
     * spent on anything nor refundable by {@code respec}, which can only give back what it can
     * still resolve. The login refund is the only path that returns them.
     * Any bump re-runs {@link #applyWebMigration} at login.
     */
    public static final int CURRENT_VERSION = 4;

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
    // Spells the web taught this player and that they did NOT already know. Written when a node
    // carrying a learn_spell effect is allocated, read when one is refunded.
    //
    // Why a ledger and not a recompute: spell knowledge lives in PlayerSpellData as one flat set
    // shared with the teacher, so "did a node give you this?" is not answerable from the spell data
    // alone. Recording only spells the player lacked at allocation time is what keeps respec from
    // confiscating a lesson they paid a teacher Knuts for, while still closing the obvious exploit
    // (allocate, pocket the spell, refund the point, spend it elsewhere).
    //
    // Absent in pre-existing saves, which load as empty — nothing recorded means nothing revoked,
    // so no migration is needed and no existing player loses a spell.
    private final Set<String> webTaughtSpells = new LinkedHashSet<>();
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
        addSkillPoints(amount, SkillSystemAPI.MAX_SKILL_POINTS);
    }

    /**
     * Adds earned points, clamped so {@code totalPointsEarned} never exceeds {@code cap}. No-op once the
     * cap is reached. Callers pass the player's per-audience cap (see {@link SkillSystemAPI#pointCapFor});
     * every audience shares {@link SkillSystemAPI#MAX_SKILL_POINTS} today, so this behaves identically to
     * the constant-clamped path.
     */
    public void addSkillPoints(int amount, int cap) {
        int grant = Math.min(amount, Math.max(0, cap - totalPointsEarned));
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

    public int getPotionBrewPoints() { return potionBrewPoints; }
    public void addPotionBrewPoints(int amount) { potionBrewPoints = Math.max(0, potionBrewPoints + amount); }

    public int getPlantsHarvested() { return plantsHarvested; }
    public void incrementPlantsHarvested() { plantsHarvested = Math.max(0, plantsHarvested + 1); }

    public int getArithmancyInteractions() { return arithmancyInteractions; }

    public int getRunicInteractions() { return runicInteractions; }

    public int getDivinationEvents() { return divinationEvents; }

    /** Number of distinct lore tomes/plaques studied — feeds KNOWLEDGE and the History of Magic OWL grade. */
    public int getLoreItemsRead() { return loreEntriesRead.size(); }
    /** Records a lore source as studied; returns true only the first time (i.e. when it actually counts). */
    public boolean markLoreEntryRead(String loreId) {
        return loreId != null && !loreId.isBlank() && loreEntriesRead.add(loreId);
    }
    /** Records a milestone as achieved; returns true only the first time (i.e. when the bump should fire). */
    public boolean markMilestoneAchieved(String milestoneId) {
        return milestoneId != null && !milestoneId.isBlank() && milestonesAchieved.add(milestoneId);
    }

    public int getAstronomyEvents() { return astronomyEvents; }

    public int getMuggleItems() { return muggleItems; }

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

    /** Spells the web taught that the player did not already know — the respec revoke list. */
    public Set<String> getWebTaughtSpells() {
        return java.util.Collections.unmodifiableSet(webTaughtSpells);
    }

    /** Records that a node taught {@code spellId}. Call only when the player did not already know it. */
    public void recordWebTaughtSpell(String spellId) {
        webTaughtSpells.add(spellId);
    }

    /** Drops {@code spellId} from the ledger once its granting node is no longer allocated. */
    public void forgetWebTaughtSpell(String spellId) {
        webTaughtSpells.remove(spellId);
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
        NbtHelper.saveStringSet(tag, "WebTaughtSpells", webTaughtSpells);
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
        webTaughtSpells.clear();
        webTaughtSpells.addAll(NbtHelper.loadStringSet(tag, "WebTaughtSpells"));
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
