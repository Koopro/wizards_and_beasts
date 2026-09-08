package at.koopro.wizardsandbeasts.wand.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * A wand that already answers to you, plus the tools to make more.
 *
 * <p><b>Bonded on the way out.</b> A freshly built wand has no master, and an unbonded wand casts at
 * a penalty and refuses outright above a threshold — which reads, to anyone testing spells rather
 * than wands, as the spell system being broken. Bonding it here is the single most useful thing this
 * kit does; the alternative was a wandmaking session before every spell test.
 *
 * <p>The debug wand and a blank come along too, because the two things you reach for after "I have a
 * wand" are "show me what the wand is doing" and "let me make a different one".
 */
@NullMarked
public final class WandDevKit implements FeatureDevKit {

    /** Elder and phoenix feather: the loudest combination, so an effect that fires is visible. */
    private static final WandWood DEV_WOOD = WandWood.ELDER;
    private static final WandCore DEV_CORE = WandCore.PHOENIX_FEATHER;
    private static final WandLength DEV_LENGTH = WandLength.STANDARD;
    private static final WandFlexibility DEV_FLEX = WandFlexibility.values()[0];

    @Override
    public String id() {
        return "wands";
    }

    @Override
    public String title() {
        return "Wands";
    }

    @Override
    public String summary() {
        return "An elder/phoenix wand already bonded to the player, plus the debug wand and a blank.";
    }

    @Override
    public void kit(ServerPlayer target, DevLog log) {
        ItemStack wand = WandItem.createWand(DEV_WOOD, DEV_CORE, DEV_LENGTH, DEV_FLEX);
        bondTo(wand, target);
        target.getInventory().add(wand);
        log.changed("wand", DEV_WOOD.getDisplayName() + " / " + DEV_CORE.getDisplayName()
                + ", bonded to " + target.getName().getString());

        target.getInventory().add(new ItemStack(WandItemRegistry.DEBUG_WAND.get()));
        log.changed("debug wand", "given");
        target.getInventory().add(new ItemStack(WandItemRegistry.WAND_BLANK.get(), 4));
        log.changed("wand blanks", 4);
    }

    /**
     * Makes the wand the player's own.
     *
     * <p>Both halves are needed: the master decides <em>whose</em> it is, and the allegiance score
     * decides how well it answers. Setting one without the other leaves a wand that is nominally
     * yours and still fights you.
     */
    private static void bondTo(ItemStack wand, ServerPlayer target) {
        wand.set(WandComponents.WAND_MASTER.get(), java.util.Optional.of(target.getUUID()));
        wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
    }
}
