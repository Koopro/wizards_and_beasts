package at.koopro.neo.data;

import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.Spells;
import at.koopro.neo.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.*;

import at.koopro.neo.registry.ModAttachments;

public class PlayerSpellData implements ModAttachments.NbtSerializable {

    /**
     * On-disk schema version. Bump when the NBT layout changes and add a
     * {@code migrateV(n-1)toVn} step in {@link PlayerSpellDataMigrator}.
     * Independent of the wire format used by sync packets.
     */
    public static final int CURRENT_VERSION = 1;

    /** NBT key for the schema version field. */
    public static final String VERSION_KEY = "DataVersion";

    public static final int LOADOUT_SIZE = 4;

    private final Set<String> knownSpells = new HashSet<>();
    private final String[] loadout = new String[LOADOUT_SIZE];
    private int activeSlot = 0;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Integer> castCount = new HashMap<>();

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

    /** Read-only view of the cooldown map (spellId -> expiry tick). */
    public Map<String, Long> getCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    /** Read-only view of the cast-count map (spellId -> lifetime casts). */
    public Map<String, Integer> getCastCounts() {
        return Collections.unmodifiableMap(castCount);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, CURRENT_VERSION);
        NbtHelper.saveStringSet(tag, "KnownSpells", knownSpells);
        for (int i = 0; i < loadout.length; i++) {
            NbtHelper.saveNullableString(tag, "Slot" + i, loadout[i]);
        }
        tag.putInt("ActiveSlot", activeSlot);
        NbtHelper.saveStringLongMap(tag, "Cooldowns", cooldowns);
        NbtHelper.saveStringIntMap(tag, "CastCount", castCount);
        return tag;
    }

    public void load(CompoundTag tag) {
        PlayerSpellDataMigrator.migrate(tag);

        knownSpells.clear();
        knownSpells.addAll(NbtHelper.loadStringSet(tag, "KnownSpells"));

        Arrays.fill(loadout, null);
        for (int i = 0; i < loadout.length; i++) {
            String val = tag.getString("Slot" + i).orElse("");
            if (!val.isEmpty()) {
                loadout[i] = val;
            }
        }

        activeSlot = tag.getInt("ActiveSlot").orElse(0);
        cooldowns.clear();
        cooldowns.putAll(NbtHelper.loadStringLongMap(tag, "Cooldowns"));
        castCount.clear();
        castCount.putAll(NbtHelper.loadStringIntMap(tag, "CastCount"));
    }

    public PlayerSpellData copy() {
        PlayerSpellData copy = new PlayerSpellData();
        CompoundTag tag = this.save();
        copy.load(tag);
        return copy;
    }
}
