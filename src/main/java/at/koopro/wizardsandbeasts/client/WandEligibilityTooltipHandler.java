package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandCastLines;
import at.koopro.wizardsandbeasts.wand.WandEligibility;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import at.koopro.wizardsandbeasts.ability.grant.AbilityKey;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityGrantState;
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

        ItemStack wand = event.getItemStack();
        WandEligibility.Result result = WandEligibility.evaluate(player, wand);
        // The relationship first, as a sentence; then why it will not serve, if it will not.
        if (result.detailLine1() != null) {
            tooltip.add(result.detailLine1());
        }
        if (result.detailLine3() != null) {
            tooltip.add(Component.literal("  ").append(result.detailLine3()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (result.reason() != null) {
            tooltip.add(Component.literal("  ").append(result.reason())
                    .withStyle(result.eligible() ? ChatFormatting.GOLD : ChatFormatting.DARK_RED));
        }

        var registries = player.level().registryAccess();
        // Wandlore is a discipline, so reading a wand is a trained skill rather than a keypress. Without
        // Appraisal the shift view says only that there is more here than you can see; the two Wandlore nodes
        // are what open the wand's character and then its figures.
        boolean appraises = ClientAbilityGrantState.hasAbility(AbilityKey.of("wand_appraisal"));
        boolean readsFigures = ClientAbilityGrantState.hasAbility(AbilityKey.of("wandlore_figures"));
        if (isShiftDown()) {
            if (!appraises) {
                tooltip.add(Component.translatable("wandcraft.tooltip.character.untrained")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                List<Component> character = WandEligibility.characterLines(wand, registries);
                if (!character.isEmpty()) {
                    tooltip.add(Component.translatable("wandcraft.tooltip.character")
                            .withStyle(ChatFormatting.DARK_AQUA));
                    tooltip.addAll(character);
                }
            }
            // What it adds to a spell, for anyone who has learned to read the figures — beneath its
            // character, not above it.
            if (readsFigures) {
                WandCastLines.append(WandStatsResolver.resolve(wand, registries), tooltip::add);
            }
        } else {
            tooltip.add(Component.translatable("wandcraft.tooltip.character.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean isShiftDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
