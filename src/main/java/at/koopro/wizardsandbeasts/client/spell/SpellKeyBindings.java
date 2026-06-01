package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class SpellKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spells"));

    /**
     * Default: KEY_NONE — GLFW_KEY_K is already bound to {@link #SKILL_MENU}.
     * Players can rebind in Controls settings.
     */
    public static final KeyMapping CHARACTER_SHEET = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".character_sheet",
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    /**
     * Default: KEY_NONE — GLFW_KEY_K is already bound to {@link #SKILL_MENU}.
     * Players can rebind in Controls settings.
     */
    public static final KeyMapping TOGGLE_STAT_HUD = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".toggle_stat_hud",
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping SPELL_UP = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_up",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY);
    public static final KeyMapping SPELL_RIGHT = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_right",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);
    public static final KeyMapping SPELL_DOWN = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_down",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY);
    public static final KeyMapping SPELL_LEFT = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_left",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);
    public static final KeyMapping SPELL_MENU = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping SKILL_MENU = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".skill_menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping OBSCURIAL_TOGGLE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".obscurial_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping OBSCURIAL_STRESS_VENT = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".obscurial_stress_vent",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping APPARATE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".apparate",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping LEGILIMENCY = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".legilimency",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping OBSCURIAL_ABILITY_PRIMARY = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".obscurial_ability_primary",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping OBSCURIAL_ABILITY_SECONDARY = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".obscurial_ability_secondary",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CHARACTER_SHEET);
        event.register(TOGGLE_STAT_HUD);
        event.register(SPELL_UP);
        event.register(SPELL_RIGHT);
        event.register(SPELL_DOWN);
        event.register(SPELL_LEFT);
        event.register(SPELL_MENU);
        event.register(SKILL_MENU);
        event.register(OBSCURIAL_TOGGLE);
        event.register(OBSCURIAL_STRESS_VENT);
        event.register(APPARATE);
        event.register(LEGILIMENCY);
        event.register(OBSCURIAL_ABILITY_PRIMARY);
        event.register(OBSCURIAL_ABILITY_SECONDARY);
    }

    private SpellKeyBindings() {
    }
}
