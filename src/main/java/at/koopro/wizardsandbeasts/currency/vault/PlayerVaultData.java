package at.koopro.wizardsandbeasts.currency.vault;

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

    public void depositKnuts(long amount) { knuts += requireCoins(amount); }
    public void depositSickles(long amount) { sickles += requireCoins(amount); }
    public void depositGalleons(long amount) { galleons += requireCoins(amount); }

    private static long requireCoins(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative deposit: " + amount);
        }
        return amount;
    }

    /** Whether the vault holds at least {@code price} Knuts' worth, in any coins. */
    public boolean canAfford(long price) {
        return price <= 0 || getTotalInKnuts() >= price;
    }

    /**
     * Pays {@code price} Knuts, all or nothing.
     *
     * <p>Refused, the vault is untouched — every coin of every denomination. That is the point of this method: the
     * pattern it replaces ("withdraw what you can, then deposit the partial back as Knuts") kept the value of a
     * refused customer's vault but broke their Galleons and Sickles into Knuts. Accepted, Knuts are spent first,
     * then Sickles, then Galleons, and the change from a broken coin comes back in the largest coins.
     *
     * @return whether the price was paid
     */
    public boolean pay(long price) {
        if (price < 0) {
            throw new IllegalArgumentException("negative price: " + price);
        }
        if (price == 0) {
            return true;
        }
        if (!canAfford(price)) {
            return false;
        }
        long taken = withdrawSmartKnuts(price);
        if (taken != price) {
            // Unreachable while every denomination is non-negative (load() guarantees it): the total covered the
            // price, and the smart withdrawal breaks coins until it is met.
            throw new IllegalStateException("vault paid " + taken + " of " + price);
        }
        return true;
    }

    /** Whether {@code amount} Knuts would fit under {@link CurrencyHelper#MAX_VAULT_KNUTS}. */
    public boolean canCredit(long amount) {
        requireCoins(amount);
        return getTotalInKnuts() <= CurrencyHelper.MAX_VAULT_KNUTS - amount;
    }

    /**
     * Credits {@code amount} Knuts in the largest coins, as Gringotts pays a wage: 500 Knuts arrive as one Galleon
     * and seven Knuts, not as a pile of Knuts.
     *
     * @return whether it was credited; refused (and the vault untouched) when it would pass
     *     {@link CurrencyHelper#MAX_VAULT_KNUTS}
     */
    public boolean credit(long amount) {
        if (!canCredit(amount)) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        long[] coins = CurrencyHelper.fromKnuts(amount);
        galleons += coins[0];
        sickles += coins[1];
        knuts += coins[2];
        return true;
    }

    public boolean withdrawKnuts(long amount) {
        if (amount < 0 || knuts < amount) return false; // a negative withdrawal would mint coins
        knuts -= amount;
        return true;
    }

    public boolean withdrawSickles(long amount) {
        if (amount < 0 || sickles < amount) return false; // a negative withdrawal would mint coins
        sickles -= amount;
        return true;
    }

    public boolean withdrawGalleons(long amount) {
        if (amount < 0 || galleons < amount) return false; // a negative withdrawal would mint coins
        galleons -= amount;
        return true;
    }

    /**
     * Withdraw knuts, auto-breaking sickles and galleons as needed (like a bank making change).
     * Returns the number of knuts actually withdrawn.
     */
    public long withdrawSmartKnuts(long amount) {
        if (amount <= 0) return 0; // a negative withdrawal would mint coins
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
        if (amount <= 0) return 0; // a negative withdrawal would mint coins
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
        // A negative coin count can only come from a hand-edited or corrupt save; read it as none, so no total
        // goes negative and pay()'s smart withdrawal always meets a price the total covers.
        knuts = Math.max(0L, tag.getLong("Knuts").orElse(0L));
        sickles = Math.max(0L, tag.getLong("Sickles").orElse(0L));
        galleons = Math.max(0L, tag.getLong("Galleons").orElse(0L));
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
