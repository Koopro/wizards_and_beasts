package at.koopro.wizardsandbeasts.apparition.splinch;

import org.jspecify.annotations.NullMarked;

/**
 * How badly an attempt went, and what that costs. Each constant carries its own consequences so the resolver
 * stays a lookup and the outcome cannot drift from the table it was specified in.
 *
 * <p>None of these kill. Splinching in the books is bloody and agonising and never fatal on the page — Susan
 * Bones loses a leg in lessons, Ron loses part of an arm escaping the Ministry, and both survive. The damage
 * here is clamped at the source so it can never take a wizard below half a heart.
 *
 * @param damage            hit points taken on arrival
 * @param effectAmplifier   {@code Splinched} amplifier, or {@code -1} for no effect
 * @param effectTicks       how long the wound lasts
 * @param fixedItemDrops    whole items torn loose, chosen at random
 * @param inventoryFraction share of the pack torn loose instead, when {@code fixedItemDrops} is zero
 * @param arrives           whether the wizard gets where they were going at all
 * @param lockoutTicks      how long before they can try again
 */
@NullMarked
public enum SplinchTier {

    /** Whole and where you meant to be. */
    CLEAN(0.0f, -1, 0, 0, 0.0f, true, 0),

    /** You arrive, and something of yours does not. */
    MINOR(4.0f, 0, 1200, 1, 0.0f, true, 0),

    /** You arrive badly, and a quarter of what you carried is still back there. */
    MAJOR(10.0f, 1, 7200, 0, 0.25f, true, 0),

    /** You do not arrive. You are torn, you are still standing where you started, and you cannot try again. */
    CATASTROPHIC(14.0f, 1, 7200, 0, 0.40f, false, 6000);

    private final float damage;
    private final int effectAmplifier;
    private final int effectTicks;
    private final int fixedItemDrops;
    private final float inventoryFraction;
    private final boolean arrives;
    private final int lockoutTicks;

    SplinchTier(float damage, int effectAmplifier, int effectTicks, int fixedItemDrops,
                float inventoryFraction, boolean arrives, int lockoutTicks) {
        this.damage = damage;
        this.effectAmplifier = effectAmplifier;
        this.effectTicks = effectTicks;
        this.fixedItemDrops = fixedItemDrops;
        this.inventoryFraction = inventoryFraction;
        this.arrives = arrives;
        this.lockoutTicks = lockoutTicks;
    }

    public float damage() {
        return damage;
    }

    public int effectAmplifier() {
        return effectAmplifier;
    }

    public int effectTicks() {
        return effectTicks;
    }

    public int fixedItemDrops() {
        return fixedItemDrops;
    }

    public float inventoryFraction() {
        return inventoryFraction;
    }

    public boolean arrives() {
        return arrives;
    }

    public int lockoutTicks() {
        return lockoutTicks;
    }

    public boolean isSplinch() {
        return this != CLEAN;
    }

    public boolean appliesEffect() {
        return effectAmplifier >= 0;
    }
}
