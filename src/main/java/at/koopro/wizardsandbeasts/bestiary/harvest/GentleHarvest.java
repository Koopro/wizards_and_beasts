package at.koopro.wizardsandbeasts.bestiary.harvest;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;

/**
 * The two rewards a careful handler earns, applied the same way to every route a creature's materials come by:
 * grooming, what it sheds, what a bonded creature gives, and the study-gated harvest on a kill.
 *
 * <ul>
 *   <li><b>Gentle Hands</b> ({@link GameplayStat#GENTLE_HARVEST}): a quarter more of what is taken, per level --
 *       whole extra items for whole multiples, the remainder as a chance of one more.</li>
 *   <li><b>Completed research</b> on the species (the field journal): the wait between yields is halved.</li>
 * </ul>
 *
 * <p>Before 2026-10-23 both applied only to the kill harvest, so the skill that promises "unicorn hair on a thorn"
 * and the reward for patiently studying an animal paid out for killing it, and for nothing else.
 */
@NullMarked
public final class GentleHarvest {

    private GentleHarvest() {}

    /** Pure: how many items beyond {@code count} a hand of this gentleness takes, given a roll in {@code [0, 1)}. */
    public static int extra(int count, float gentleness, float roll) {
        if (count <= 0 || gentleness <= 0f) {
            return 0;
        }
        float more = count * gentleness;
        int whole = (int) more;
        return whole + (roll < more - whole ? 1 : 0);
    }

    /** Pure: the wait before the next yield, halved for a researched species. */
    public static int cooldown(int ticks, boolean researched) {
        return researched ? ticks / 2 : ticks;
    }

    public static float gentleness(ServerPlayer player) {
        return SkillSystemAPI.getGameplayBonus(player, GameplayStat.GENTLE_HARVEST);
    }

    /** Whether the player has completed the field-journal research on any page for this species. */
    public static boolean researched(ServerPlayer player, EntityType<?> species) {
        return BestiaryDataHelper.researchComplete(player, species);
    }

    /** {@link #extra(int, float, float)} for this player, rolled on their random source. */
    public static int extraFor(ServerPlayer player, int count) {
        return extra(count, gentleness(player), player.getRandom().nextFloat());
    }
}
