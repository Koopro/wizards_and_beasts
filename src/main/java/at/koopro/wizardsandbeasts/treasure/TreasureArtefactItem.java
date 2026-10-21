package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A piece of recovered tomb treasure. The item is only its look; what it <em>is</em> — where it came from, the curse
 * on it, how deep, its worth — lives in {@link TreasureArtefact} on the stack, and the tooltip tells only what the
 * holder has learned: an unread curse is a warning, not a diagnosis.
 */
@NullMarked
public class TreasureArtefactItem extends Item {

    public TreasureArtefactItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        TreasureArtefact artefact = stack.get(ModDataComponents.TREASURE_ARTEFACT.get());
        if (artefact == null) {
            return;
        }
        tooltipAdder.accept(Component.translatable("treasure.wizards_and_beasts.found_at",
                Component.translatable("subject." + artefact.site().getNamespace() + "." + artefact.site().getPath()))
                .withStyle(ChatFormatting.GRAY));
        if (artefact.cursed()) {
            if (artefact.identified()) {
                CurseKind curse = artefact.curse().orElseThrow();
                tooltipAdder.accept(Component.translatable("treasure.wizards_and_beasts.curse_known",
                        curse.displayName(), artefact.layers()).withStyle(ChatFormatting.DARK_RED));
                tooltipAdder.accept(Component.translatable("treasure.wizards_and_beasts.counter_hint",
                        Component.translatable("spell.wizards_and_beasts." + curse.counterSpell() + ".name"))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            } else {
                tooltipAdder.accept(Component.translatable("treasure.wizards_and_beasts.curse_unknown")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            }
        } else {
            tooltipAdder.accept(Component.translatable("treasure.wizards_and_beasts.clean")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (artefact.identified()) {
            tooltipAdder.accept(Component.translatable("treasure.wizards_and_beasts.worth",
                    CurrencyHelper.formatFromKnuts(artefact.valueKnuts())).withStyle(ChatFormatting.GOLD));
        }
    }
}
