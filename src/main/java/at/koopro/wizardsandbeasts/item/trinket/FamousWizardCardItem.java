package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.card.WizardCard;
import at.koopro.wizardsandbeasts.card.WizardCards;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A Famous Wizard Card: the thing you were actually after when you bought the chocolate.
 *
 * <p><b>The wizard is the item.</b> The stack is named after whoever is on the card and coloured
 * by that card's tier, so a chest of cards reads as a collection at a glance rather than as
 * twenty-four stacks of "Famous Wizard Card" that have to be hovered one by one. Which portrait is
 * drawn comes from the same component, via a {@code minecraft:select} in
 * {@code assets/wizards_and_beasts/items/famous_wizard_card.json}.
 *
 * <p>A card with no id at all is a blank — the crafted card, before anyone is printed on it.
 *
 * @see WizardCards for the roster and the draw weights
 */
@NullMarked
public class FamousWizardCardItem extends Item {

    public FamousWizardCardItem(Properties properties) {
        super(properties);
    }

    /** One card, drawn by rarity weight. */
    public static ItemStack randomCard(RandomSource random) {
        return of(WizardCards.random(random));
    }

    public static ItemStack randomCard(Level level) {
        return randomCard(level.random);
    }

    /** A printed card for a specific wizard. */
    public static ItemStack of(WizardCard card) {
        ItemStack stack = new ItemStack(ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get());
        stack.set(ModDataComponents.WIZARD_CARD_ID.get(), card.id());
        return stack;
    }

    /** The wizard printed on this stack, or null when the card is blank. */
    public static @Nullable WizardCard cardOf(ItemStack stack) {
        return WizardCards.byId(stack.get(ModDataComponents.WIZARD_CARD_ID.get()));
    }

    @Override
    public Component getName(ItemStack stack) {
        WizardCard card = cardOf(stack);
        return card == null
                ? super.getName(stack)
                : Component.translatable(card.nameKey()).withStyle(card.rarity().colour());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        WizardCard card = cardOf(stack);
        if (card == null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.famous_wizard_card.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // The deed, then the set number: exactly what the back of a real card carries, and the
        // name is already the stack's own name so it is not repeated here.
        tooltipAdder.accept(Component.translatable(card.achievementKey())
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.famous_wizard_card.number",
                        Component.translatable(card.rarity().translationKey())
                                .withStyle(card.rarity().colour()),
                        WizardCards.number(card),
                        WizardCards.ALL.size())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
