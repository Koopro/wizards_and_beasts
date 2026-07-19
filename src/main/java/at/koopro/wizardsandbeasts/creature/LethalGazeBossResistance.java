package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Basilisk-specific (more precisely: {@code Trait.LETHAL_GAZE}-tier boss) resistance to a handful of
 * spells, consulted from {@code SpellProjectileEntity.onHitEntity}. A plain static-method utility
 * rather than a new {@code CreatureAbility.onSpellHit} hook — {@code CreatureAbility} is deliberately
 * melee/tick/death-only, and adding a spell-hit hook to that interface for exactly one creature would
 * be disproportionate. Gating on {@code Trait.LETHAL_GAZE} (rather than a hardcoded creature id) means
 * any future second lethal-gaze boss automatically inherits the same resistances.
 */
public final class LethalGazeBossResistance {

    /** Avada Kedavra deals fixed damage to a lethal-gaze boss rather than its default near-instant-kill damage. */
    public static final float AVADA_KEDAVRA_FIXED_DAMAGE = 100.0f;
    /** Stupefy only has a coin-flip chance to actually slow a lethal-gaze boss. */
    private static final float STUPEFY_EFFECT_CHANCE = 0.5f;

    private LethalGazeBossResistance() {}

    public static boolean isBossTarget(Entity target) {
        return target instanceof GenericBeastEntity beast && beast.has(Trait.LETHAL_GAZE);
    }

    public static float resolveAvadaKedavraDamage(float defaultDamage, boolean isBoss) {
        return isBoss ? AVADA_KEDAVRA_FIXED_DAMAGE : defaultDamage;
    }

    public static boolean allowsStupefyEffect(RandomSource random, boolean isBoss) {
        return !isBoss || random.nextFloat() < STUPEFY_EFFECT_CHANCE;
    }
}
