package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Making and reading artefact stacks. */
@NullMarked
public final class TreasureArtefacts {

    private TreasureArtefacts() {}

    /** A fresh find from {@code site}: one of its artefact items, cursed and unread. */
    public static ItemStack create(Identifier siteId, TreasureSite site, RandomSource random) {
        Item item = BuiltInRegistries.ITEM.getValue(site.rollItem(random));
        if (item == Items.AIR) {
            item = CurrencyItemRegistry.ANCIENT_GOBLET.get();
        }
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.TREASURE_ARTEFACT.get(), site.roll(siteId, random));
        return stack;
    }

    public static @Nullable TreasureArtefact of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModDataComponents.TREASURE_ARTEFACT.get());
    }

    public static void set(ItemStack stack, TreasureArtefact artefact) {
        stack.set(ModDataComponents.TREASURE_ARTEFACT.get(), artefact);
    }

    /** The hand holding an artefact (main hand first), or {@code null}. */
    public static @Nullable InteractionHand handHolding(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (of(player.getItemInHand(hand)) != null) {
                return hand;
            }
        }
        return null;
    }
}
