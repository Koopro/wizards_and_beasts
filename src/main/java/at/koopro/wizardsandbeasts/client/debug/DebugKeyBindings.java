package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Registers keybinds for the Model Debug Editor.
 * <p>
 * F6 = toggle editor, F7 = export JSON, F8 = switch vanilla/GeckoLib mode.
 */
public final class DebugKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug"));

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".debug_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY);

    public static final KeyMapping EXPORT = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".debug_export",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY);

    public static final KeyMapping SWITCH_MODEL_MODE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".debug_model_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(EXPORT);
        event.register(SWITCH_MODEL_MODE);
    }

    private DebugKeyBindings() {}
}
