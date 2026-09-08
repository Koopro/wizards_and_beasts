package at.koopro.wizardsandbeasts.entity.debug;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * One of every broom, because the point of per-broom identity is the comparison.
 *
 * <p>A Nimbus should not feel like a Cleansweep, and there is no way to know whether it does without
 * both in the same session. Handing over one of each — built from the loaded definition sheet rather
 * than a hard-coded list, so a new broom appears here the moment it is defined — is the only way to
 * test the thing the system exists for.
 */
@NullMarked
public final class BroomDevKit implements FeatureDevKit {

    private static final int POLISH = 16;

    @Override
    public String id() {
        return "brooms";
    }

    @Override
    public String title() {
        return "Broom Flight";
    }

    @Override
    public String summary() {
        return "One of every defined broom, plus polish - per-broom feel only tests by comparison.";
    }

    @Override
    public void kit(ServerPlayer target, DevLog log) {
        var definitions = BroomDefinitionRegistry.getAll();
        if (definitions.isEmpty()) {
            log.warn("no broom definitions loaded - the datapack listener has not run");
            return;
        }
        for (BroomDefinition definition : definitions) {
            ItemStack broom = new ItemStack(BroomItemRegistry.BROOM_ITEM.get());
            broom.set(ModDataComponents.BROOM_DEFINITION.get(), definition.id());
            target.getInventory().add(broom);
        }
        log.changed("brooms", definitions.size() + " - one of each defined variant");

        target.getInventory().add(new ItemStack(BroomItemRegistry.BROOM_POLISH.get(), POLISH));
        log.changed("broom polish", POLISH);
    }
}
