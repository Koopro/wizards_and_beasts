package at.koopro.wizardsandbeasts.client.cloak;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.cloak.CloakItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class CloakClientViewRestrictionHandler {

    private CloakClientViewRestrictionHandler() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        AvatarRenderState state = event.getRenderState();
        if (state.id != mc.player.getId()) {
            return;
        }

        ItemStack chestItem = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestItem.getItem() instanceof CloakItem cloakItem) || !cloakItem.isDeathlyHallow()) {
            return;
        }

        boolean thirdPersonWorld = mc.options.getCameraType() != CameraType.FIRST_PERSON;
        boolean inventoryPreview = mc.screen instanceof AbstractContainerScreen<?>;
        if (thirdPersonWorld || inventoryPreview) {
            state.leftHandItemState.clear();
            state.rightHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
            state.rightHandItemStack = ItemStack.EMPTY;
            state.headEquipment = ItemStack.EMPTY;
            state.chestEquipment = ItemStack.EMPTY;
            state.legsEquipment = ItemStack.EMPTY;
            state.feetEquipment = ItemStack.EMPTY;
        }
    }
}
