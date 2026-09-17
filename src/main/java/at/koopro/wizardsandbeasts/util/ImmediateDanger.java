package at.koopro.wizardsandbeasts.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NullMarked;

/**
 * Whether a player is in danger right now: badly hurt, or being hunted.
 *
 * <p>One reading shared by the systems that care — a wand whose bond is forged in hardship, and the Decree
 * for the Reasonable Restriction of Underage Sorcery, which allows magic "in life-threatening situations" —
 * so the two cannot disagree about the same moment.
 */
@NullMarked
public final class ImmediateDanger {

    /** Something hunting the player within this many blocks counts. */
    public static final double HUNTER_RADIUS = 16.0;

    private ImmediateDanger() {}

    public static boolean test(ServerPlayer player) {
        if (player.getHealth() <= player.getMaxHealth() * 0.5f) {
            return true;
        }
        return !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HUNTER_RADIUS),
                mob -> mob.isAlive() && mob.getTarget() == player).isEmpty();
    }
}
