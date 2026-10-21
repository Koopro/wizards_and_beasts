package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AbilityProficiency;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * How many destinations a wizard can hold in mind at once.
 *
 * <p>Practice, not a flat allowance: a novice keeps four places straight and a master twelve. Twelve is also
 * the old flat cap and {@link PlayerApparitionPoints#MAX_POINTS}, so a player who had already filled their
 * list never wakes up over the limit — the change costs nothing at endgame and gives the early game something
 * to earn.
 */
@NullMarked
public final class ApparitionAnchors {

    /** Destinations a wizard can hold at proficiency 0. */
    public static final int BASE_CAPACITY = 4;
    /** Extra destinations earned across the whole proficiency curve. */
    public static final int PROFICIENCY_CAPACITY = 8;
    /** What the {@code apparition_recall} skill node adds on top of the proficiency cap. */
    public static final int NODE_BONUS = 2;

    private ApparitionAnchors() {}

    /** The cap for {@code proficiency}, before any skill node. */
    public static int capacity(float proficiency) {
        float clamped = Math.max(0.0f, Math.min(1.0f, proficiency));
        return BASE_CAPACITY + (int) Math.floor(clamped * PROFICIENCY_CAPACITY);
    }

    /** The hard ceiling for this player: twelve, or the Explorer capstone's sixteen. */
    public static int ceiling(ServerPlayer player) {
        return at.koopro.wizardsandbeasts.skill.build.capstone.SeasonedTraveller.has(player)
                ? PlayerApparitionPoints.SEASONED_CEILING : PlayerApparitionPoints.MAX_POINTS;
    }

    /** The cap this player is actually held to, skill nodes included. */
    public static int capacity(ServerPlayer player) {
        int capacity = capacity(AbilityProficiency.get(player, AbilityIds.APPARITION));
        if (SkillSystemAPI.hasAbility(player, "apparition_recall")) {
            capacity += NODE_BONUS;
        }
        if (at.koopro.wizardsandbeasts.skill.build.capstone.SeasonedTraveller.has(player)) {
            capacity += at.koopro.wizardsandbeasts.skill.build.capstone.SeasonedTraveller.EXTRA_DESTINATIONS;
        }
        return capacity;
    }
}
