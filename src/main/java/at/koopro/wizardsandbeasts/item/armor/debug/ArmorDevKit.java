package at.koopro.wizardsandbeasts.item.armor.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.registry.ArmorItemRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.NullMarked;

/**
 * Every robe set at once, because armour is a rig and rigs are judged side by side.
 *
 * <p>These are GeckoLib models drawn over the player, and the failures they have historically had —
 * a hood inside the skull, a detail cube swallowed by an inflated chest box, a piece that renders
 * as a featureless slab — are only obvious against another piece that works. Handing over all three
 * sets and both headpieces means you can put them on in turn without leaving the spot you are
 * standing in.
 */
@NullMarked
public final class ArmorDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "armor";
    }

    @Override
    public String title() {
        return "Worn Armour";
    }

    @Override
    public String summary() {
        return "Student, Auror and Death Eater sets plus hat and mask - rigs are judged side by side.";
    }

    @Override
    public void kit(ServerPlayer target, DevLog log) {
        give(target, "student robe", ArmorItemRegistry.STUDENT_ROBE_CHEST,
                ArmorItemRegistry.STUDENT_ROBE_LEGS, ArmorItemRegistry.STUDENT_ROBE_BOOTS);
        give(target, "auror robe", ArmorItemRegistry.AUROR_ROBE_CHEST,
                ArmorItemRegistry.AUROR_ROBE_LEGS, ArmorItemRegistry.AUROR_ROBE_BOOTS);
        give(target, "death eater robe", ArmorItemRegistry.DEATH_EATER_ROBE_CHEST,
                ArmorItemRegistry.DEATH_EATER_ROBE_LEGS, ArmorItemRegistry.DEATH_EATER_ROBE_BOOTS);
        give(target, "headwear", ArmorItemRegistry.WIZARD_HAT, ArmorItemRegistry.DEATH_EATER_MASK);
        log.changed("armour", "3 sets, a hat and a mask");
    }

    @SafeVarargs
    private static void give(ServerPlayer target, String label, DeferredItem<?>... items) {
        for (DeferredItem<?> item : items) {
            target.getInventory().add(new ItemStack(item.get()));
        }
    }
}
