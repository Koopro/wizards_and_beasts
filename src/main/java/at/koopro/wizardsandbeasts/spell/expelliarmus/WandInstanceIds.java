package at.koopro.wizardsandbeasts.spell.expelliarmus;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class WandInstanceIds {
    private WandInstanceIds() {}

    public static UUID getOrAssign(ServerLevel level, ItemStack stack) {
        UUID existing = stack.get(ModDataComponents.WAND_INSTANCE_ID.get());
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        stack.set(ModDataComponents.WAND_INSTANCE_ID.get(), id);
        return id;
    }
}
