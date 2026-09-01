package at.koopro.wizardsandbeasts.dittany;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Essence of Dittany. A few drops will heal most wounds.
 *
 * <p>It used to be six hit points, free, on a five-second cooldown — better than a golden apple and
 * simpler than one. What it does now is smaller and more interesting: four points, or six if there is
 * an actual <em>wound</em> to close, and it can be poured on somebody else.
 *
 * <p>The bonus is the whole design. Dittany in the books is not a healing potion, it is what Hermione
 * uses on Ron's splinched arm and on Harry's curse-cut — it is for <em>injuries</em>, and it works
 * better on one than on a bruise. So a wizard at half health gets four; a wizard bleeding from a
 * Sectumsempra gets six and the bleeding stops.
 */
@NullMarked
public final class Dittany {

    /** Ordinary healing, in half-hearts. */
    public static final float BASE_HEAL = 4.0f;

    /** Extra healing when there is a real wound to close. */
    public static final float WOUND_BONUS = 2.0f;

    /** Nine seconds. Enough to matter in a fight without making it the only thing you do. */
    public static final int COOLDOWN_TICKS = 180;

    /**
     * The injuries Dittany closes, and the ones it works better on.
     *
     * <p>A tag, so a pack's own bleed or curse-residue is mended without this class knowing it
     * exists. Deliberately narrow: this is for wounds, not for every harmful effect — Dittany is not
     * a cure-all and a tag full of debuffs would make it one.
     *
     * <p>Splinching is <em>not</em> in here. It is already cured through the separate
     * {@code cures_splinch} item tag by {@code SplinchDamageHandler}, which fires off the use-item
     * event — one of the reasons self-application stayed a use-over-time rather than becoming an
     * instant click.
     */
    public static final TagKey<MobEffect> MENDS = TagKey.create(
            Registries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dittany_mends"));

    private Dittany() {}

    /** Whether this patient has an injury Dittany answers particularly well. */
    public static boolean isWounded(LivingEntity patient) {
        for (MobEffectInstance instance : patient.getActiveEffects()) {
            if (instance.getEffect().is(MENDS)) {
                return true;
            }
        }
        return false;
    }

    /** What a dose is worth on this patient. */
    public static float healingFor(LivingEntity patient) {
        return isWounded(patient) ? BASE_HEAL + WOUND_BONUS : BASE_HEAL;
    }

    /**
     * Closes every wound the patient is carrying.
     *
     * @return how many were closed
     */
    public static int mend(LivingEntity patient) {
        List<Holder<MobEffect>> wounds = new ArrayList<>();
        for (MobEffectInstance instance : patient.getActiveEffects()) {
            if (instance.getEffect().is(MENDS)) {
                wounds.add(instance.getEffect());
            }
        }
        // Collected first: removeEffect mutates the collection getActiveEffects is a view of.
        for (Holder<MobEffect> wound : wounds) {
            patient.removeEffect(wound);
        }
        return wounds.size();
    }

    /** Whether there is any point pouring a dose on this patient at all. */
    public static boolean needsTreatment(LivingEntity patient) {
        return patient.getHealth() < patient.getMaxHealth() || isWounded(patient);
    }
}
