package at.koopro.wizardsandbeasts.client.equipment.gui;

import at.koopro.wizardsandbeasts.client.equipment.curios.WardrobeCuriosClient;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.equipment.WardrobeLayout;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.NullMarked;

/**
 * The wardrobe (UI renaissance P6): the player's Curios slots as an open armoire. All item moves go through
 * {@link WardrobeMenu}'s Curios slots on the server; the only extra input is the eye on each look slot, which asks the server
 * to flip Curios' render flag for that slot.
 */
@NullMarked
public final class WardrobeScreen extends AbstractContainerScreen<WardrobeMenu> {

    public WardrobeScreen(WardrobeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WardrobeLook.W;
        this.imageHeight = WardrobeLook.H;
        this.inventoryLabelX = WardrobeLayout.INV_X;
        this.inventoryLabelY = WardrobeLayout.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int n = WardrobeLayout.PLACES.size();
        boolean[] worn = new boolean[n];
        boolean[] shown = new boolean[n];
        boolean[] sealed = new boolean[n];
        for (int i = 0; i < n; i++) {
            Slot wornSlot = menu.slots.get(i * 2);
            worn[i] = wornSlot.hasItem();
            shown[i] = menu.shown(i);
            sealed[i] = !wornSlot.isActive();
        }
        WardrobeLook.drawBackground(g, font, leftPos, topPos, title, worn, shown, sealed);
        if (minecraft != null && minecraft.player != null) {
            WardrobeLook.drawReflection(g, leftPos, topPos, minecraft.player, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // The title is on the brass plate (drawn with the background); only the inventory's label here.
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WizardsPalette.PAGE_INK_2, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int eye = WardrobeLook.eyeAt(leftPos, topPos, mouseX, mouseY);
        if (eye >= 0 && menu.canToggle(eye)) {
            g.setTooltipForNextFrame(font, Component.translatable(menu.shown(eye)
                    ? "gui.wizards_and_beasts.wardrobe.eye.hide" : "gui.wizards_and_beasts.wardrobe.eye.show"),
                    mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.isActive()) {
            int index = menu.slots.indexOf(hoveredSlot);
            if (index >= 0 && index < WardrobeLayout.WARDROBE_SLOTS) {
                g.setTooltipForNextFrame(font,
                        WardrobeLook.slotName(WardrobeLayout.PLACES.get(index / 2), index % 2 == 1), mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int eye = WardrobeLook.eyeAt(leftPos, topPos, event.x(), event.y());
        if (eye >= 0 && event.button() == 0 && menu.canToggle(eye)) {
            WardrobeCuriosClient.toggleRender(eye);
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
