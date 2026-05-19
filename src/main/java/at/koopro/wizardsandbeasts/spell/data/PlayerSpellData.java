package at.koopro.wizardsandbeasts.spell.data;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.util.NbtHelper;
import at.koopro.wizardsandbeasts.util.NamespaceMigration;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.*;

import at.koopro.wizardsandbeasts.registry.ModAttachments;

public class PlayerSpellData implements ModAttachments.NbtSerializable {
    public enum MasteryTier {
        NOVICE,
        PROFICIENT,
        MASTERED
    }

    /**
     * On-disk schema version. Bump when the NBT layout changes and add a
     * {@code migrateV(n-1)toVn} step in {@link PlayerSpellDataMigrator}.
     * Independent of the wire format used by sync packets.
     */
    public static final int CURRENT_VERSION = 2;

    /** NBT key for the schema version field. */
    public static final String VERSION_KEY = "DataVersion";
    public static final String NS_MIGRATED_KEY = "NamespaceMigrated";

    public static final int LOADOUT_SIZE = 4;

    private final Set<String> knownSpells = new HashSet<>();
    private final String[] loadout = new String[LOADOUT_SIZE];
    private int activeSlot = 0;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Integer> castCount = new HashMap<>();
    private final Map<String, Integer> successfulHits = new HashMap<>();
    private final Map<String, Float> spellProficiencies = new HashMap<>();
    private final Map<String, Integer> rejectCounts = new HashMap<>();
    private int syncCorrections;
    // NEW FIELD — OWL DEFENCE_AGAINST_DARK_ARTS: spells cast against hostile mobs
    private int combatSpellCasts;

    public Set<String> getKnownSpells() {
        return Collections.unmodifiableSet(knownSpells);
    }

    public boolean knowsSpell(String spellId) {
        return knownSpells.contains(spellId);
    }

    public void learnSpell(String spellId) {
        knownSpells.add(spellId);
    }

    public void forgetSpell(String spellId) {
        knownSpells.remove(spellId);
        for (int i = 0; i < loadout.length; i++) {
            if (spellId.equals(loadout[i])) {
                loadout[i] = null;
            }
        }
    }

    public void resetAll() {
        knownSpells.clear();
        Arrays.fill(loadout, null);
        activeSlot = 0;
        cooldowns.clear();
        castCount.clear();
        successfulHits.clear();
        spellProficiencies.clear();
        rejectCounts.clear();
        syncCorrections = 0;
    }

    @Nullable
    public String getLoadoutSpell(int slot) {
        if (slot < 0 || slot >= loadout.length) return null;
        return loadout[slot];
    }

    public void setLoadoutSpell(int slot, @Nullable String spellId) {
        if (slot < 0 || slot >= loadout.length) return;
        loadout[slot] = spellId;
    }

    public String[] getLoadout() {
        return loadout;
    }

    public int getActiveSlot() {
        return activeSlot;
    }

    public void setActiveSlot(int slot) {
        if (slot >= 0 && slot < loadout.length) {
            this.activeSlot = slot;
        }
    }

    @Nullable
    public String getActiveSpellId() {
        return getLoadoutSpell(activeSlot);
    }

    @Nullable
    public Spell getActiveSpell() {
        String id = getActiveSpellId();
        return id != null ? Spells.byId(id) : null;
    }

    public boolean isOnCooldown(String spellId, long currentTick) {
        Long expiry = cooldowns.get(spellId);
        return expiry != null && currentTick < expiry;
    }

    public void setCooldown(String spellId, long expiryTick) {
        cooldowns.put(spellId, expiryTick);
    }

    public long getCooldownExpiry(String spellId) {
        return cooldowns.getOrDefault(spellId, 0L);
    }

    public int getCastCount(String spellId) {
        return castCount.getOrDefault(spellId, 0);
    }

    public void incrementCastCount(String spellId) {
        castCount.merge(spellId, 1, Integer::sum);
    }

    public int getSuccessfulHits(String spellId) {
        return successfulHits.getOrDefault(spellId, 0);
    }

    public MasteryTier getMasteryTier(String spellId) {
        int hits = getSuccessfulHits(spellId);
        if (hits >= at.koopro.wizardsandbeasts.spell.core.Proficiency.MASTERED.getCastsRequired()) {
            return MasteryTier.MASTERED;
        }
        if (hits >= at.koopro.wizardsandbeasts.spell.core.Proficiency.PROFICIENT.getCastsRequired()) {
            return MasteryTier.PROFICIENT;
        }
        return MasteryTier.NOVICE;
    }

    public boolean hasReachedMasteryTier(String spellId, MasteryTier requiredTier) {
        if (requiredTier == null) {
            return true;
        }
        return getMasteryTier(spellId).ordinal() >= requiredTier.ordinal();
    }

    public void incrementSuccessfulHits(String spellId) {
        successfulHits.merge(spellId, 1, Integer::sum);
    }

    public float getSpellProficiency(String spellId) {
        return spellProficiencies.getOrDefault(spellId, 0.0f);
    }

    public void setSpellProficiency(String spellId, float proficiency) {
        float clamped = Math.max(0.0f, Math.min(1.0f, proficiency));
        if (clamped <= 0.0f) {
            spellProficiencies.remove(spellId);
        } else {
            spellProficiencies.put(spellId, clamped);
        }
    }

    public void clearSpellProficiency(String spellId) {
        spellProficiencies.remove(spellId);
    }

    /**
     * Sets a spell's lifetime cast count to an absolute value. Intended for
     * applying snapshot syncs from the server; gameplay should normally use
     * {@link #incrementCastCount(String)} instead.
     */
    public void setCastCount(String spellId, int casts) {
        if (casts <= 0) {
            castCount.remove(spellId);
        } else {
            castCount.put(spellId, casts);
        }
    }

    public void setSuccessfulHits(String spellId, int hits) {
        if (hits <= 0) {
            successfulHits.remove(spellId);
        } else {
            successfulHits.put(spellId, hits);
        }
    }

    /** Read-only view of the cooldown map (spellId -> expiry tick). */
    public Map<String, Long> getCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    /** Read-only view of the cast-count map (spellId -> lifetime casts). */
    public Map<String, Integer> getCastCounts() {
        return Collections.unmodifiableMap(castCount);
    }

    /** Read-only view of the successful-hit map (spellId -> lifetime successful effects/hits). */
    public Map<String, Integer> getSuccessfulHitCounts() {
        return Collections.unmodifiableMap(successfulHits);
    }

    public Map<String, Float> getSpellProficiencies() {
        return Collections.unmodifiableMap(spellProficiencies);
    }

    public void incrementRejectReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        rejectCounts.merge(reason, 1, Integer::sum);
    }

    public Map<String, Integer> getRejectCounts() {
        return Collections.unmodifiableMap(rejectCounts);
    }

    public void setRejectCount(String reason, int count) {
        if (reason == null || reason.isBlank()) return;
        if (count <= 0) {
            rejectCounts.remove(reason);
        } else {
            rejectCounts.put(reason, count);
        }
    }

    /** Clears spell/wand rejection telemetry counters only (debug / support). */
    public void clearRejectCounts() {
        rejectCounts.clear();
    }

    public int getSyncCorrections() {
        return syncCorrections;
    }

    public void incrementSyncCorrections() {
        syncCorrections++;
    }

    public void setSyncCorrections(int value) {
        syncCorrections = Math.max(0, value);
    }

    public int getCombatSpellCasts() { return combatSpellCasts; }
    public void incrementCombatSpellCasts() { combatSpellCasts = Math.max(0, combatSpellCasts + 1); }
    public void setCombatSpellCasts(int value) { combatSpellCasts = Math.max(0, value); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, CURRENT_VERSION);
        tag.putBoolean(NS_MIGRATED_KEY, true);
        NbtHelper.saveStringSet(tag, "KnownSpells", knownSpells);
        for (int i = 0; i < loadout.length; i++) {
            NbtHelper.saveNullableString(tag, "Slot" + i, loadout[i]);
        }
        tag.putInt("ActiveSlot", activeSlot);
        NbtHelper.saveStringLongMap(tag, "Cooldowns", cooldowns);
        NbtHelper.saveStringIntMap(tag, "CastCount", castCount);
        NbtHelper.saveStringIntMap(tag, "SuccessfulHits", successfulHits);
        NbtHelper.saveStringFloatMap(tag, "SpellProficiencies", spellProficiencies);
        NbtHelper.saveStringIntMap(tag, "RejectCount", rejectCounts);
        tag.putInt("SyncCorrections", syncCorrections);
        tag.putInt("CombatSpellCasts", combatSpellCasts);
        return tag;
    }

    public void load(CompoundTag tag) {
        PlayerSpellDataMigrator.migrate(tag);

        knownSpells.clear();
        for (String id : NbtHelper.loadStringSet(tag, "KnownSpells")) {
            knownSpells.add(NamespaceMigration.remapLegacyId(id));
        }

        Arrays.fill(loadout, null);
        for (int i = 0; i < loadout.length; i++) {
            String val = tag.getString("Slot" + i).orElse("");
            if (!val.isEmpty()) {
                loadout[i] = NamespaceMigration.remapLegacyId(val);
            }
        }

        activeSlot = tag.getInt("ActiveSlot").orElse(0);
        cooldowns.clear();
        for (Map.Entry<String, Long> entry : NbtHelper.loadStringLongMap(tag, "Cooldowns").entrySet()) {
            cooldowns.put(NamespaceMigration.remapLegacyId(entry.getKey()), entry.getValue());
        }
        castCount.clear();
        for (Map.Entry<String, Integer> entry : NbtHelper.loadStringIntMap(tag, "CastCount").entrySet()) {
            castCount.put(NamespaceMigration.remapLegacyId(entry.getKey()), entry.getValue());
        }
        successfulHits.clear();
        for (Map.Entry<String, Integer> entry : NbtHelper.loadStringIntMap(tag, "SuccessfulHits").entrySet()) {
            successfulHits.put(NamespaceMigration.remapLegacyId(entry.getKey()), entry.getValue());
        }
        spellProficiencies.clear();
        for (Map.Entry<String, Float> entry : NbtHelper.loadStringFloatMap(tag, "SpellProficiencies").entrySet()) {
            spellProficiencies.put(NamespaceMigration.remapLegacyId(entry.getKey()), Math.max(0.0f, Math.min(1.0f, entry.getValue())));
        }
        rejectCounts.clear();
        rejectCounts.putAll(NbtHelper.loadStringIntMap(tag, "RejectCount"));
        syncCorrections = tag.getInt("SyncCorrections").orElse(0);
        combatSpellCasts = tag.getInt("CombatSpellCasts").orElse(0);
    }

    public PlayerSpellData copy() {
        PlayerSpellData copy = new PlayerSpellData();
        CompoundTag tag = this.save();
        copy.load(tag);
        return copy;
    }
}
