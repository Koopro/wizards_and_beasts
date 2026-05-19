package at.koopro.wizardsandbeasts.item.darkartefact;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public interface IHorcruxVessel {
    boolean isSoulIntact(@NonNull ItemStack stack);
}
