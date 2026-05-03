package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Handles per-tick keybind polling and scroll-wheel interception
 * for the Model Debug Editor.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class DebugInputHandler {

    /** Cooldown to prevent rapid-fire cycling from held keys. */
    private static int cycleCooldown = 0;
    private static final int COOLDOWN_TICKS = 4;

    private DebugInputHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ModelDebugEditor editor = ModelDebugEditor.get();

        // Toggle keybinds (one-shot)
        if (DebugKeyBindings.TOGGLE.consumeClick()) {
            editor.toggle();
        }
        if (!editor.isActive()) return;

        if (DebugKeyBindings.EXPORT.consumeClick()) {
            DebugConfigExporter.save(editor.getAllData());
        }
        if (DebugKeyBindings.SWITCH_MODEL_MODE.consumeClick()) {
            editor.toggleModelMode();
        }

        // Cooldown for held-key cycling
        if (cycleCooldown > 0) {
            cycleCooldown--;
            return;
        }

        Window window = Minecraft.getInstance().getWindow();

        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_UP)) {
            editor.cyclePart(-1);
            cycleCooldown = COOLDOWN_TICKS;
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DOWN)) {
            editor.cyclePart(1);
            cycleCooldown = COOLDOWN_TICKS;
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_TAB)) {
            editor.cycleMode();
            cycleCooldown = COOLDOWN_TICKS;
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_X)) {
            editor.setAxis(ModelDebugEditor.Axis.X);
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_Y)) {
            editor.setAxis(ModelDebugEditor.Axis.Y);
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_Z)) {
            editor.setAxis(ModelDebugEditor.Axis.Z);
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_R)) {
            editor.resetCurrentPart();
            cycleCooldown = COOLDOWN_TICKS;
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        ModelDebugEditor editor = ModelDebugEditor.get();
        if (!editor.isActive()) return;

        // Consume scroll to prevent hotbar changes
        event.setCanceled(true);

        Window window = Minecraft.getInstance().getWindow();
        float step;
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)) {
            step = 0.01f;   // fine
        } else if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)) {
            step = 0.25f;   // coarse
        } else {
            step = 0.05f;   // default
        }

        float delta = (float) event.getScrollDeltaY() * step;
        editor.adjustValue(delta);
    }
}
