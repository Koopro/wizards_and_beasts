package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class ItemDescriptionTooltipHandler {

    private ItemDescriptionTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !WizardsAndBeastsMod.MODID.equals(itemId.getNamespace())) {
            return;
        }

        String descriptionKey = stack.getItem().getDescriptionId() + ".desc";
        if (!I18n.exists(descriptionKey)) {
            return;
        }

        event.getToolTip().add(Component.translatable(descriptionKey)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
