package at.koopro.wizardsandbeasts.client.event;

import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
/**
 * Adds a "Character Sheet" button to the vanilla Inventory screen
 * via {@link ScreenEvent.Init.Post}.
 *
 * <p>Button is positioned below the recipe-book button (top-left of inventory panel).
 * Skipped when {@link Module#CHARACTER_SHEET} is disabled.
 */
public final class InventoryScreenInjector {

    // Vanilla InventoryScreen background is 176 × 166; recipe book at leftPos-97, topPos+3.
    // We sit directly below it: leftPos-97, topPos+25.
    private static final int BUTTON_W = 20;
    private static final int BUTTON_H = 20;

    private InventoryScreenInjector() {}

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ModuleManager.isEnabled(Module.CHARACTER_SHEET)) return;
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        // Compute inventory origin (background 176×166, centered)
        int guiLeft = (inv.width  - 176) / 2;
        int guiTop  = (inv.height - 166) / 2;

        int bx = guiLeft - 97;
        int by = guiTop  + 25; // 3px gap + recipe-book height (20) + 2px

        Button btn = Button.builder(
                        Component.translatable("gui.wizards_and_beasts.character_sheet.tooltip"),
                        b -> Minecraft.getInstance().setScreen(new CharacterSheetScreen()))
                .bounds(bx, by, BUTTON_W, BUTTON_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.wizards_and_beasts.character_sheet.tooltip")))
                .build();

        event.addListener(btn);
    }
}
