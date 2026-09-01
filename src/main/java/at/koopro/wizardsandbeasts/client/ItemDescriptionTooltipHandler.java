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

    /** Ceiling on `.desc.N` continuation lines, so a bad lang file cannot fill the screen. */
    private static final int MAX_DESCRIPTION_LINES = 6;

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

        // Optional continuation lines, `<desc>.2`, `.3`, ... — a tooltip does not wrap, so an item
        // whose description is a *rule* rather than a label had to choose between saying half of it
        // and running off the edge of the screen. Numbered keys let the author break the line where
        // it reads best instead of where the window happens to end, and a translator can break it
        // somewhere else.
        //
        // Opt-in and self-terminating: an item with no `.desc.2` behaves exactly as before, and the
        // scan stops at the first gap rather than probing to the cap, so a missing `.2` can never
        // hide a stray `.3`.
        for (int line = 2; line <= MAX_DESCRIPTION_LINES; line++) {
            String continuation = descriptionKey + "." + line;
            if (!I18n.exists(continuation)) {
                break;
            }
            event.getToolTip().add(Component.translatable(continuation)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
