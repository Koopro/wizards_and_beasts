package at.koopro.wizardsandbeasts.brew.silver;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Silver on a blade, and what it is worth against the things silver is for.
 *
 * <p>A weapon is silvered once, permanently, by pure silver drawn out of an Occamy shell; after that
 * it bites harder on anything the pack calls a dark creature. Both halves are deliberately small:
 * one boolean component and one entity tag, so the mechanic reaches werewolves, Dementors, Inferi and
 * anything a datapack adds without this class learning a single mob's name.
 *
 * <p><b>Flat bonus, not a multiplier.</b> Silver helps a dagger and a battleaxe by the same amount,
 * which is what makes it worth applying to a starter weapon rather than something you save for the
 * best sword you will ever own. A multiplier would have made silvering a late-game tax.
 */
@NullMarked
public final class SilveredWeapons {

    /**
     * Extra damage a silvered weapon deals to a dark creature, in half-hearts.
     *
     * <p>Four is a Smite-II-ish step: clearly worth the shell, and clearly not a replacement for
     * actually bringing a decent weapon.
     */
    public static final float BONUS_DAMAGE = 4.0f;

    /**
     * What silver is effective against.
     *
     * <p>Hand-authored, because "is this a dark creature" is a judgement about the fiction and not
     * something derivable from a registry name. Ships with the mod's Dementor and the vanilla undead
     * that read as the same kind of thing.
     */
    public static final TagKey<EntityType<?>> DARK_CREATURES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dark_creatures"));

    private SilveredWeapons() {}

    /** Whether this weapon has been silvered. */
    public static boolean isSilvered(ItemStack stack) {
        return !stack.isEmpty()
                && Boolean.TRUE.equals(stack.get(ModDataComponents.SILVERED.get()));
    }

    /**
     * Silvers a weapon.
     *
     * @return {@code false} if it was already silvered — silvering twice buys nothing and the caller
     *         should not spend the player's silver on it
     */
    public static boolean silver(ItemStack stack) {
        if (stack.isEmpty() || isSilvered(stack)) {
            return false;
        }
        stack.set(ModDataComponents.SILVERED.get(), true);
        return true;
    }

    /** Whether silver bites this target. */
    public static boolean isDarkCreature(Entity target) {
        return target.getType().is(DARK_CREATURES);
    }

    /**
     * Extra damage for one blow, or {@code 0} when either half of the pairing is missing.
     *
     * <p>Pure, so the sum a player feels can be tested without a level or a mob.
     */
    public static float bonusFor(ItemStack weapon, boolean targetIsDark) {
        return isSilvered(weapon) && targetIsDark ? BONUS_DAMAGE : 0.0f;
    }

    /**
     * Whether a stack can be silvered at all.
     *
     * <p>Anything with durability. That is a deliberately loose test: a wizard silvering a shovel is
     * doing something eccentric rather than something broken, and the alternative — a hand-kept list
     * of weapon items — would silently exclude every weapon added by every other mod.
     */
    public static boolean canBeSilvered(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && !isSilvered(stack);
    }
}
