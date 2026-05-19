package at.koopro.wizardsandbeasts.item.bestiary;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class BestiaryItem extends Item {
    public BestiaryItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            try {
                Class<?> hooks = Class.forName("at.koopro.wizardsandbeasts.client.network.ClientScreenHooks");
                hooks.getMethod("openBestiaryScreen").invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return InteractionResult.SUCCESS;
    }
}
