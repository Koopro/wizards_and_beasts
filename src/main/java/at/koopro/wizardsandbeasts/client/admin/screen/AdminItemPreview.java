package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandPresetRegistry;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

/**
 * Display stacks for the Wands and Travel previews, drawn by the game's own item renderers — no second rendering
 * path. A wand is made by {@link WandmakingRecipe#createWand}, the bench's own routine, so the preview carries exactly
 * the components a crafted wand would; a broom is its registered item with its definition component.
 */
@NullMarked
final class AdminItemPreview {

    private AdminItemPreview() {}

    /** A wand of this make, for display only (never sent anywhere). */
    static ItemStack wand(String wood, String core, float length, String flexibility, String preset) {
        Identifier woodId = Identifier.tryParse(wood);
        Identifier coreId = Identifier.tryParse(core);
        if (woodId == null || coreId == null) {
            return ItemStack.EMPTY;
        }
        WandFlexibility flex = null;
        for (WandFlexibility candidate : WandFlexibility.values()) {
            if (candidate.name().equalsIgnoreCase(flexibility)) {
                flex = candidate;
            }
        }
        // Recipes are not synced to clients; a throwaway recipe runs the bench's own assembly for these ids.
        ItemStack stack = new WandmakingRecipe(woodId, coreId, 0f, length, length, 1f).createWand(flex, length);
        Identifier presetId = preset.isEmpty() ? null : Identifier.tryParse(preset);
        if (presetId != null) {
            WandPresetRegistry.get(presetId).ifPresent(p -> stack.set(WandComponents.WAND_CONFIGURATION.get(), p.configuration()));
        }
        return stack;
    }

    /** The broom's own item, showing this definition; empty when no broom item carries that id. */
    static ItemStack broom(String definitionId) {
        Identifier id = Identifier.tryParse(definitionId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (!(item instanceof BroomItem)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.BROOM_DEFINITION.get(), id);
        return stack;
    }

    /** Draws {@code stack} {@code scale} times the size of an inventory icon, top-left at (x, y). */
    static void draw(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    static List<Component> tooltip(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (stack.isEmpty() || mc.level == null) {
            return List.of();
        }
        return stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
    }

    static Component flexibilityName(String flexibility) {
        return Component.translatable("wandcraft.flexibility." + flexibility.toLowerCase(Locale.ROOT));
    }
}
