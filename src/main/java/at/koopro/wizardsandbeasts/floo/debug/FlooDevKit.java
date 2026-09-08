package at.koopro.wizardsandbeasts.floo.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Two grates and the powder to use them.
 *
 * <p><b>Two</b> fireplaces, not one. Floo is a network, and a network with a single node cannot be
 * tested at all — you cannot travel, cannot see an address list with anything on it, and cannot
 * reproduce a misfire. The second block is the whole kit.
 */
@NullMarked
public final class FlooDevKit implements FeatureDevKit {

    private static final int FIREPLACES = 2;
    private static final int POWDER = 64;

    @Override
    public String id() {
        return "floo";
    }

    @Override
    public String title() {
        return "Floo Network";
    }

    @Override
    public String summary() {
        return "Powder and two fireplaces - one grate is not a network you can travel on.";
    }

    @Override
    public void kit(ServerPlayer target, DevLog log) {
        target.getInventory().add(new ItemStack(MiscItemRegistry.FLOO_POWDER.get(), POWDER));
        log.changed("floo powder", POWDER);
        target.getInventory().add(new ItemStack(ModBlocks.FLOO_FIREPLACE_ITEM.get(), FIREPLACES));
        log.changed("fireplaces", FIREPLACES + " - place both, then /wandb world floo register");
    }

    /**
     * Clears the arrival cooldown so the next hop is immediate.
     *
     * <p>The cooldown is the single most common reason a fire does nothing, and waiting it out
     * between every attempt is most of the time cost of testing travel.
     */
    @Override
    public void open(ServerPlayer target, DevLog log) {
        long remaining = FlooTravelHandler.cooldownRemaining(target);
        if (remaining <= 0) {
            log.skip("no arrival cooldown to clear");
            return;
        }
        FlooTravelHandler.clearCooldown(target);
        log.changed("arrival cooldown cleared", remaining + "t remaining");
    }
}
