package at.koopro.wizardsandbeasts.skill.build.capstone;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Wandmaster capstone, {@code wandkeepers_hands}: wandlore as a craft, not a statistic.
 *
 * <p>Two things change in the world:
 * <ul>
 *   <li><b>Mending.</b> Reparo on a worn or damaged wand in the other hand restores it. Before this only the Elder
 *       Wand could, and the only other mend was Ollivander's counter. A snapped wand stays beyond anyone but the
 *       Elder Wand — Hermione's Reparo could not save Harry's.</li>
 *   <li><b>Strange wands.</b> A wand that belongs to someone else answers a Wandkeeper as {@code RELUCTANT}
 *       instead of {@code UNFAMILIAR}: it works at reluctant strength and, because only an unfamiliar or broken
 *       wand backfires, a hawthorn picked up from a fallen duellist no longer turns on the hand holding it. The
 *       wand's allegiance does not change — winning it is still a duel away.</li>
 * </ul>
 */
@NullMarked
public final class WandkeepersHands {

    public static final String ABILITY = "wandkeepers_hands";

    private WandkeepersHands() {}

    /** How a wand in this caster's hand behaves, given its recorded bond state. */
    public static WandBondState answeringState(Player caster, WandBondState recorded) {
        return answeringState(recorded, caster instanceof ServerPlayer server && SkillSystemAPI.hasAbility(server, ABILITY));
    }

    /** Pure rule: a Wandkeeper lifts UNFAMILIAR to RELUCTANT and leaves every other state alone. */
    public static WandBondState answeringState(WandBondState recorded, boolean wandkeeper) {
        return wandkeeper && recorded == WandBondState.UNFAMILIAR ? WandBondState.RELUCTANT : recorded;
    }

    /** Whether this caster may mend {@code integrity} with Reparo (Elder Wand cases are decided by the caller). */
    public static boolean mayMend(ServerPlayer caster, float integrity) {
        return mayMend(SkillSystemAPI.hasAbility(caster, ABILITY), integrity);
    }

    /** Pure rule: a Wandkeeper mends anything short of snapped, and nothing already sound. */
    public static boolean mayMend(boolean wandkeeper, float integrity) {
        return wandkeeper && integrity > WandAllegianceRules.BROKEN_AT && integrity < 1.0f;
    }
}
