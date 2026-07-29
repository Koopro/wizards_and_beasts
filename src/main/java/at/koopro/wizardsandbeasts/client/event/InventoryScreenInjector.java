package at.koopro.wizardsandbeasts.client.event;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetTextures;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jspecify.annotations.NonNull;
/**
 * Adds a "Character Sheet" button to the vanilla Inventory screen
 * via {@link ScreenEvent.Init.Post}.
 *
 * <p>The button sits immediately to the right of the vanilla recipe-book button and
 * tracks it. Skipped when {@link Module#CHARACTER_SHEET} is disabled.
 */
public final class InventoryScreenInjector {

    /** Recipe-book button geometry, from {@code InventoryScreen#getRecipeBookButtonPosition}. */
    private static final int RECIPE_BUTTON_X = 104;
    private static final int RECIPE_BUTTON_Y_FROM_MIDDLE = -22;
    private static final int RECIPE_BUTTON_W = 20;

    private static final int GAP = 2;
    private static final int BUTTON_W = 20;
    /** Matches the recipe-book button's 18px height so the pair reads as one row. */
    private static final int BUTTON_H = 18;

    private InventoryScreenInjector() {}

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ModuleManager.isEnabled(Module.CHARACTER_SHEET)) return;
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        CharacterTabButton btn = new CharacterTabButton(inv,
                b -> Minecraft.getInstance().setScreen(new CharacterSheetScreen()));
        btn.setTooltip(Tooltip.create(
                Component.translatable("gui.wizards_and_beasts.character_sheet.tooltip")));

        event.addListener(btn);
    }

    /** Vanilla button frame with the character-sheet icon centered on it. */
    private static final class CharacterTabButton extends Button {
        private static final int ICON = 16;

        private final InventoryScreen screen;

        CharacterTabButton(InventoryScreen screen, OnPress onPress) {
            super(0, 0, BUTTON_W, BUTTON_H, Component.empty(), onPress, DEFAULT_NARRATION);
            this.screen = screen;
            anchor();
        }

        /**
         * Re-anchors beside the recipe-book button.
         *
         * <p>Re-applied per frame rather than set once at init because opening the recipe
         * book moves the inventory: {@code AbstractRecipeBookScreen}'s toggle handler
         * reassigns {@code leftPos} and repositions its own button by hand, without
         * re-running {@code init()}. A button placed once would be left behind the moment
         * the book was opened.
         */
        private void anchor() {
            setX(screen.getGuiLeft() + RECIPE_BUTTON_X + RECIPE_BUTTON_W + GAP);
            setY(screen.height / 2 + RECIPE_BUTTON_Y_FROM_MIDDLE);
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // AbstractButton#renderWidget is final and does nothing but call this, so this is
            // the whole render pass: re-anchor, draw the frame, then the icon in place of a label.
            anchor();
            renderDefaultSprite(g);
            int ix = getX() + (getWidth() - ICON) / 2;
            int iy = getY() + (getHeight() - ICON) / 2;
            McStylePanel.drawTexture(g, CharacterSheetTextures.ICON_TAB, ix, iy, ICON, ICON, ICON, ICON);
        }
    }
}
