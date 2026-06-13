package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandEligibility;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Appends wand eligibility lines to wand item tooltips.
 *
 * <p>Simple mode: status line (+ reason when ineligible). Advanced mode (Shift held):
 * additionally a separator and the detail lines from {@link WandEligibility.Result}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class WandEligibilityTooltipHandler {

    private WandEligibilityTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!WandHelper.isWand(event.getItemStack())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();

        @Nullable Player player = event.getEntity();
        if (player == null) {
            tooltip.add(Component.translatable("wandcraft.eligibility.unknown")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        WandEligibility.Result result = WandEligibility.evaluate(player, event.getItemStack());
        if (result.eligible()) {
            tooltip.add(Component.translatable("wandcraft.eligibility.can_use")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("wandcraft.eligibility.cannot_use")
                    .withStyle(ChatFormatting.RED));
            if (result.reason() != null) {
                tooltip.add(Component.literal("  ").append(result.reason())
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }

        if (isShiftDown()) {
            tooltip.add(Component.literal("——————")
                    .withStyle(ChatFormatting.DARK_GRAY));
            boolean any = false;
            for (@Nullable Component detail : new Component[]{
                    result.detailLine1(), result.detailLine2(), result.detailLine3()}) {
                if (detail != null) {
                    tooltip.add(detail.copy().withStyle(ChatFormatting.GRAY));
                    any = true;
                }
            }
            if (!any) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("wandcraft.eligibility.no_data"))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static boolean isShiftDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
