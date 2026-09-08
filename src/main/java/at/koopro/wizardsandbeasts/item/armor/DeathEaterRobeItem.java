package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.function.Consumer;

/**
 * Death Eater robe — the Auror kit's mirror, traded for toughness bought with dark enchantment.
 *
 * <p>The only hooded set in the wardrobe: the chest piece carries both hood bones and the wearer
 * switches between them with the hood keybind. See {@link RobeHood}.
 */
public class DeathEaterRobeItem extends WizardArmorItem implements HoodedRobe {

    public DeathEaterRobeItem(Properties properties, ArmorType armorType) {
        super(properties, WizardArmorMaterials.DEATH_EATER_ROBE, armorType);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.DeathEaterRobeRenderer";
    }

    /**
     * The hood state, and how to change it.
     *
     * <p>A keybind with no other surface is a feature nobody finds. The line is on the chest piece
     * only — {@link HoodedRobe#hasHood} is what decides which piece of a set wears the hood.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);

        if (!RobeHood.hasHood(stack)) {
            return;
        }

        tooltipAdder.accept(Component.translatable(RobeHood.isUp(stack)
                        ? "tooltip.wizards_and_beasts.hood_up"
                        : "tooltip.wizards_and_beasts.hood_down")
                .withStyle(ChatFormatting.GRAY));
    }
}
