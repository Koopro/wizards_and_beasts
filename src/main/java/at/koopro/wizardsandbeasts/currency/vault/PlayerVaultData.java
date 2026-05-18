package at.koopro.wizardsandbeasts.currency.vault;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import net.minecraft.nbt.CompoundTag;

import at.koopro.wizardsandbeasts.registry.ModAttachments;

public class PlayerVaultData implements ModAttachments.NbtSerializable {
    public static final String VERSION_KEY = "DataVersion";
    public static final int CURRENT_VERSION = 1;

    private long knuts;
    private long sickles;
    private long galleons;

    public long getKnuts() { return knuts; }
    public long getSickles() { return sickles; }
    public long getGalleons() { return galleons; }

    public void depositKnuts(long amount) { knuts += amount; }
    public void depositSickles(long amount) { sickles += amount; }
    public void depositGalleons(long amount) { galleons += amount; }

    public boolean withdrawKnuts(long amount) {
        if (knuts < amount) return false;
        knuts -= amount;
        return true;
    }

    public boolean withdrawSickles(long amount) {
        if (sickles < amount) return false;
        sickles -= amount;
        return true;
    }

    public boolean withdrawGalleons(long amount) {
        if (galleons < amount) return false;
        galleons -= amount;
        return true;
    }

    /**
     * Withdraw knuts, auto-breaking sickles and galleons as needed (like a bank making change).
     * Returns the number of knuts actually withdrawn.
     */
    public long withdrawSmartKnuts(long amount) {
        long needed = amount;

        // 1. Take from knuts directly
        long fromKnuts = Math.min(needed, knuts);
        knuts -= fromKnuts;
        needed -= fromKnuts;
        if (needed <= 0) return amount;

        // 2. Break sickles into knuts
        long sicklesNeeded = ceilDiv(needed, CurrencyHelper.KNUTS_PER_SICKLE);
        long sicklesUsed = Math.min(sicklesNeeded, sickles);
        if (sicklesUsed > 0) {
            sickles -= sicklesUsed;
            long knutsObtained = sicklesUsed * CurrencyHelper.KNUTS_PER_SICKLE;
            long taken = Math.min(needed, knutsObtained);
            knuts += knutsObtained - taken; // change goes back as knuts
            needed -= taken;
        }
        if (needed <= 0) return amount;

        // 3. Break galleons
        long galleonsNeeded = ceilDiv(needed, CurrencyHelper.KNUTS_PER_GALLEON);
        long galleonsUsed = Math.min(galleonsNeeded, galleons);
        if (galleonsUsed > 0) {
            galleons -= galleonsUsed;
            long knutsObtained = galleonsUsed * CurrencyHelper.KNUTS_PER_GALLEON;
            long taken = Math.min(needed, knutsObtained);
            // Put change back in highest denominations
            long change = knutsObtained - taken;
            sickles += change / CurrencyHelper.KNUTS_PER_SICKLE;
            knuts += change % CurrencyHelper.KNUTS_PER_SICKLE;
            needed -= taken;
        }

        return amount - needed;
    }

    /**
     * Withdraw sickles, auto-breaking galleons as needed.
     * Returns the number of sickles actually withdrawn.
     */
    public long withdrawSmartSickles(long amount) {
        long needed = amount;

        // 1. Take from sickles directly
        long fromSickles = Math.min(needed, sickles);
        sickles -= fromSickles;
        needed -= fromSickles;
        if (needed <= 0) return amount;

        // 2. Break galleons into sickles
        long galleonsNeeded = ceilDiv(needed, CurrencyHelper.SICKLES_PER_GALLEON);
        long galleonsUsed = Math.min(galleonsNeeded, galleons);
        if (galleonsUsed > 0) {
            galleons -= galleonsUsed;
            long sicklesObtained = galleonsUsed * CurrencyHelper.SICKLES_PER_GALLEON;
            long taken = Math.min(needed, sicklesObtained);
            sickles += sicklesObtained - taken; // change
            needed -= taken;
        }

        return amount - needed;
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    public boolean exchangeKnutsToSickle() {
        if (knuts < CurrencyHelper.KNUTS_PER_SICKLE) return false;
        knuts -= CurrencyHelper.KNUTS_PER_SICKLE;
        sickles++;
        return true;
    }

    public boolean exchangeSickleToKnuts() {
        if (sickles < 1) return false;
        sickles--;
        knuts += CurrencyHelper.KNUTS_PER_SICKLE;
        return true;
    }

    public boolean exchangeSicklesToGalleon() {
        if (sickles < CurrencyHelper.SICKLES_PER_GALLEON) return false;
        sickles -= CurrencyHelper.SICKLES_PER_GALLEON;
        galleons++;
        return true;
    }

    public boolean exchangeGalleonToSickles() {
        if (galleons < 1) return false;
        galleons--;
        sickles += CurrencyHelper.SICKLES_PER_GALLEON;
        return true;
    }

    public long getTotalInKnuts() {
        return CurrencyHelper.toKnuts(galleons, sickles, knuts);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, CURRENT_VERSION);
        tag.putLong("Knuts", knuts);
        tag.putLong("Sickles", sickles);
        tag.putLong("Galleons", galleons);
        return tag;
    }

    public void load(CompoundTag tag) {
        PlayerVaultDataMigrator.migrate(tag);
        knuts = tag.getLong("Knuts").orElse(0L);
        sickles = tag.getLong("Sickles").orElse(0L);
        galleons = tag.getLong("Galleons").orElse(0L);
    }

    public void resetAll() {
        knuts = 0;
        sickles = 0;
        galleons = 0;
    }

    public PlayerVaultData copy() {
        PlayerVaultData copy = new PlayerVaultData();
        copy.knuts = this.knuts;
        copy.sickles = this.sickles;
        copy.galleons = this.galleons;
        return copy;
    }
}
