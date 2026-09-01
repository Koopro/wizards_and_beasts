package at.koopro.wizardsandbeasts.demiguise;

import at.koopro.wizardsandbeasts.item.cloak.CloakItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * How long an ordinary Invisibility Cloak still works for.
 *
 * <p><b>The Deathly Hallow is exempt, and that is the whole point of the mechanic.</b> Every
 * invisibility cloak in the books wears out — the concealment charms fade, the cloth goes cloudy,
 * and a second-hand one is nearly worthless. Exactly one does not, and being the one that never
 * fails is what makes it a Hallow. Before this, both cloaks were unlimited and the Hallow's only
 * distinction was cosmetic.
 *
 * <p>Charges are woven in with Demiguise hair. Each is one second of <em>actual concealment</em>,
 * not one second of wearing it: a cloak in a chest and a cloak worn in daylight with nothing looking
 * for you both cost nothing. You spend it while you are hidden.
 *
 * <h2>Running out is not destruction</h2>
 * A spent cloak stops concealing and stays a cloak. It can be rewoven with more hair. A rare wearable
 * that consumed itself would be a rare wearable nobody ever risked wearing.
 */
@NullMarked
public final class CloakCharges {

    /** Seconds of concealment one Demiguise hair weaves in. */
    public static final int SECONDS_PER_HAIR = 60;

    /**
     * Ceiling on stored charge.
     *
     * <p>Half an hour of being invisible is an enormous amount of it, and a cap keeps the recharge
     * recipe from turning a stack of hairs into a permanent Hallow — which is the one thing the
     * ordinary cloak must never become.
     */
    public static final int MAX_CHARGES = 1800;

    /** Charge a freshly crafted or found ordinary cloak carries. */
    public static final int STARTING_CHARGES = 300;

    private CloakCharges() {}

    /**
     * Whether this cloak's concealment answers to charges at all.
     *
     * <p>False for the Hallow, and false for anything that is not a cloak.
     */
    public static boolean isChargeable(ItemStack stack) {
        return stack.getItem() instanceof CloakItem cloak && !cloak.isDeathlyHallow();
    }

    /**
     * Charges left, in seconds of concealment.
     *
     * <p>An ordinary cloak with no component at all reads as {@link #STARTING_CHARGES} rather than
     * as empty — so cloaks that existed before charges did keep working, and a
     * {@code /give} without NBT produces a usable cloak rather than a dead one.
     */
    public static int remaining(ItemStack stack) {
        if (!isChargeable(stack)) {
            return MAX_CHARGES;
        }
        Integer stored = stack.get(ModDataComponents.CLOAK_CHARGES.get());
        return stored == null ? STARTING_CHARGES : Math.max(0, stored);
    }

    /** Whether this cloak will still hide its wearer. */
    public static boolean hasCharge(ItemStack stack) {
        return !isChargeable(stack) || remaining(stack) > 0;
    }

    /**
     * Spends one second of concealment.
     *
     * @return {@code true} while the cloak still has charge after the spend
     */
    public static boolean spendSecond(ItemStack stack) {
        if (!isChargeable(stack)) {
            return true;
        }
        int left = remaining(stack) - 1;
        stack.set(ModDataComponents.CLOAK_CHARGES.get(), Math.max(0, left));
        return left > 0;
    }

    /** Weaves {@code hairs} worth of charge in, capped. Returns the new total. */
    public static int recharge(ItemStack stack, int hairs) {
        if (!isChargeable(stack)) {
            return MAX_CHARGES;
        }
        int total = Math.min(MAX_CHARGES, remaining(stack) + hairs * SECONDS_PER_HAIR);
        stack.set(ModDataComponents.CLOAK_CHARGES.get(), total);
        return total;
    }
}
