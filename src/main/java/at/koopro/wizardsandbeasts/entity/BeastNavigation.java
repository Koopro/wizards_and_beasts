package at.koopro.wizardsandbeasts.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

/**
 * Pathfinding shapes shared across this mod's beasts.
 *
 * <p>Five flying entities — Dementor, Augurey, Cornish Pixie, Phoenix and the generic flying beast —
 * each built the same navigator with the same two settings. It is one call now.
 */
@NullMarked
public final class BeastNavigation {

    private BeastNavigation() {
    }

    /**
     * The flying navigator every winged beast here wants: it will not open doors, and it floats
     * rather than sinking when its path crosses water.
     */
    public static FlyingPathNavigation flying(Mob mob, Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(mob, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }
}
