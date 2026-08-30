package at.koopro.wizardsandbeasts.item.bestiary;

import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
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
            ClientScreenHooksInvoker.invoke("openBestiaryScreen");
        }
        return InteractionResult.SUCCESS;
    }
}
