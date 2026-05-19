package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;

public class FamousWizardCardItem extends Item {

    public static final String[] CARD_IDS = {
            "albus_dumbledore",
            "harry_potter",
            "gilderoy_lockhart",
            "merlin",
            "circe",
            "paracelsus",
            "cliodna",
            "morgana_le_fay"
    };

    public FamousWizardCardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack randomCard(RandomSource random) {
        ItemStack stack = new ItemStack(ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get());
        stack.set(ModDataComponents.WIZARD_CARD_ID.get(), CARD_IDS[random.nextInt(CARD_IDS.length)]);
        return stack;
    }

    public static ItemStack randomCard(Level level) {
        return randomCard(level.random);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        String id = stack.get(ModDataComponents.WIZARD_CARD_ID.get());
        if (id != null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.famous_wizard_card.variant." + id)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.famous_wizard_card.blank").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
