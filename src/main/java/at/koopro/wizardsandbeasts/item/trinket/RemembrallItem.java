package at.koopro.wizardsandbeasts.item.trinket;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RemembrallItem extends Item {
    public RemembrallItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return (System.currentTimeMillis() / 400L) % 2L == 0L;
    }
}
