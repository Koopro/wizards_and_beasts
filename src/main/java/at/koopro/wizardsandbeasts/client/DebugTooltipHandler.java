package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Shows all data components on item tooltips when advanced tooltips (F3+H) are enabled.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class DebugTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getFlags().isAdvanced()) return;

        ItemStack stack = event.getItemStack();
        var tooltip = event.getToolTip();

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("[Data Components]")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        boolean any = false;
        for (DataComponentType<?> type : stack.getComponents().keySet()) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id == null) continue;

            any = true;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = stack.getComponents().get((DataComponentType) type);
            String valueStr = formatValue(value);

            tooltip.add(Component.literal("  " + id)
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" = " + valueStr)
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (!any) {
            tooltip.add(Component.literal("  (none)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        String str = value.toString();
        if (str.length() > 80) {
            return str.substring(0, 77) + "...";
        }
        return str;
    }
}
