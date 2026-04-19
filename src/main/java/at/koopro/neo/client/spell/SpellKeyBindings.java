package at.koopro.neo.client.spell;

import at.koopro.neo.Neo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class SpellKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Neo.MODID, "spells"));

    public static final KeyMapping SPELL_UP = new KeyMapping(
            "key." + Neo.MODID + ".spell_up",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY);
    public static final KeyMapping SPELL_RIGHT = new KeyMapping(
            "key." + Neo.MODID + ".spell_right",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);
    public static final KeyMapping SPELL_DOWN = new KeyMapping(
            "key." + Neo.MODID + ".spell_down",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY);
    public static final KeyMapping SPELL_LEFT = new KeyMapping(
            "key." + Neo.MODID + ".spell_left",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);
    public static final KeyMapping SPELL_MENU = new KeyMapping(
            "key." + Neo.MODID + ".spell_menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SPELL_UP);
        event.register(SPELL_RIGHT);
        event.register(SPELL_DOWN);
        event.register(SPELL_LEFT);
        event.register(SPELL_MENU);
    }

    private SpellKeyBindings() {
    }
}
