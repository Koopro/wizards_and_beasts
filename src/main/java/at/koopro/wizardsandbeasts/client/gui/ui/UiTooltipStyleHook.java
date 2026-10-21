package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * Every plain tooltip on one of this mod's screens is drawn on the night card.
 *
 * <p>One hook rather than a style argument at every call site: screens set tooltips four different ways
 * (a widget's {@code Tooltip.create}, {@code setTooltipForNextFrame} with and without lines, a tab's
 * deferred list), and a style that has to be remembered at each of them is a style half the screens
 * forget. Item tooltips are left alone -- an item's card belongs to the item (its rarity, its own
 * {@code tooltip_style} component) and looks the same in every inventory. A tooltip that already asked for
 * a style keeps it.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class UiTooltipStyleHook {

    private static final String SCREEN_PACKAGE = "at.koopro.wizardsandbeasts.";

    private UiTooltipStyleHook() {}

    @SubscribeEvent
    public static void onTooltipTexture(RenderTooltipEvent.Texture event) {
        if (!event.getItemStack().isEmpty() || event.getOriginalTexture() != null) {
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && screen.getClass().getName().startsWith(SCREEN_PACKAGE)) {
            event.setTexture(UiTooltips.STYLE);
        }
    }
}
