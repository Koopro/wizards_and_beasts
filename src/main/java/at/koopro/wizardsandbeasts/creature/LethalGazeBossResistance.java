package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.world.entity.Entity;

/**
 * Basilisk-specific (more precisely: {@code Trait.LETHAL_GAZE}-tier boss) resistance to a handful of
 * spells, consulted from {@code SpellProjectileEntity.onHitEntity}. A plain static-method utility
 * rather than a new {@code CreatureAbility.onSpellHit} hook — {@code CreatureAbility} is deliberately
 * melee/tick/death-only, and adding a spell-hit hook to that interface for exactly one creature would
 * be disproportionate. Gating on {@code Trait.LETHAL_GAZE} (rather than a hardcoded creature id) means
 * any future second lethal-gaze boss automatically inherits the same resistances.
 *
 * <p>Stupefy's coin flip used to live here. It is now the general hide rule in
 * {@code spell.resistance.MagicResistance}: a lethal-gaze boss turns enchantments aside until two land at once,
 * the same way every other magic-resistant body does, and without dice.
 */
public final class LethalGazeBossResistance {

    /** Avada Kedavra deals fixed damage to a lethal-gaze boss rather than its default near-instant-kill damage. */
    public static final float AVADA_KEDAVRA_FIXED_DAMAGE = 100.0f;

    private LethalGazeBossResistance() {}

    public static boolean isBossTarget(Entity target) {
        return target instanceof GenericBeastEntity beast && beast.has(Trait.LETHAL_GAZE);
    }

    public static float resolveAvadaKedavraDamage(float defaultDamage, boolean isBoss) {
        return isBoss ? AVADA_KEDAVRA_FIXED_DAMAGE : defaultDamage;
    }
}
