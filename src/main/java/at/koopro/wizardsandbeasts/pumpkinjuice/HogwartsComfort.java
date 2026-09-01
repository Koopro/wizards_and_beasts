package at.koopro.wizardsandbeasts.pumpkinjuice;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The ten percent a clear head is worth.
 *
 * <p>Applies to the two things the brief calls out — what you kill and what you study — and to
 * nothing else. Notably <em>not</em> to mining, smelting or breeding: this is a school drink, and
 * scaling every experience orb in the game would make it a mandatory sip before any XP farm rather
 * than a nice thing to have during a lesson.
 *
 * <h2>The rounding matters more than the percentage</h2>
 * Study awards are small integers — a cauldron brew is worth one to three points — and ten percent of
 * two, rounded, is zero. A bonus that silently never fires on the exact activity it advertises would
 * be worse than no bonus, so the fraction is paid as a <em>chance</em> instead: see
 * {@link #scaleStudy}. Over a term of brewing it comes out at the ten percent on the tin.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HogwartsComfort {

    /** The bonus, as briefed. */
    public static final float BONUS = 0.10f;

    private HogwartsComfort() {}

    /** Whether this drinker currently has a clear head. */
    public static boolean isActive(@Nullable LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.HOGWARTS_COMFORT);
    }

    /**
     * Combat experience. Scales what a mob drops when a comforted player is the one that killed it.
     *
     * <p>Hooked on the drop rather than on orb pickup so it only ever touches kills — an orb hook
     * would quietly cover furnaces and ore too.
     */
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null || !isActive(killer) || event.getDroppedExperience() <= 0) {
            return;
        }
        event.setDroppedExperience(scaleCombat(event.getDroppedExperience()));
    }

    /**
     * Combat XP with the bonus applied.
     *
     * <p>The whole bonus, floored, with a minimum of one — so a one-point mob is worth two rather
     * than the bonus vanishing on everything small, and a ten-point mob is worth eleven rather than
     * twelve.
     *
     * <p><b>The epsilon is not decoration.</b> {@code BONUS} is a float, so {@code 10 * 0.10f} is
     * {@code 1.0000000149…} in double and a bare {@code ceil} pays out two points instead of one.
     * The mod has been bitten by exactly this before — see {@code FineSchedule}'s percent thresholds
     * — and a nudge below the floor is the cheapest way not to be again.
     */
    public static int scaleCombat(int experience) {
        if (experience <= 0) {
            return experience;
        }
        int bonus = (int) Math.floor(experience * (double) BONUS + FLOAT_SLOP);
        return experience + Math.max(1, bonus);
    }

    /** Slack that absorbs float-to-double error without ever reaching the next whole point. */
    private static final double FLOAT_SLOP = 1.0e-6;

    /**
     * A study award with the bonus applied, paying the fractional part as a chance.
     *
     * <p>{@code roll} is a uniform {@code [0,1)} draw supplied by the caller so the distribution can
     * be tested at its edges rather than approximately.
     */
    public static int scaleStudy(int points, float roll) {
        if (points <= 0) {
            return points;
        }
        float bonus = points * BONUS;
        int whole = (int) bonus;
        float fraction = bonus - whole;
        return points + whole + (roll < fraction ? 1 : 0);
    }

    /** Convenience for call sites that have a player and a points award. */
    public static int scaleStudy(Player student, int points) {
        if (!isActive(student)) {
            return points;
        }
        RandomSource random = student.getRandom();
        return scaleStudy(points, random.nextFloat());
    }
}
