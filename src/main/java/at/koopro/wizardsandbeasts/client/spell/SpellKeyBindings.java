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
     * Default: C ("Character"). GLFW_KEY_K is taken by {@link #SKILL_MENU}, so the character sheet —
     * the mod's second pillar — gets its own reachable default instead of shipping unbound. Vanilla
     * has no default C binding; players can rebind in Controls settings.
     */
    public static final KeyMapping CHARACTER_SHEET = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".character_sheet",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

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
    /**
     * Hold to open the spell wheel, aim, release to arm the hovered spell into the active slot.
     *
     * <p>Default: {@code X}. Free in vanilla and unused by every other bind in this mod
     * (arrows / {@code C} / {@code G} / {@code K} here, {@code V} / {@code R} / {@code N} / {@code M} /
     * {@code B} on the ability framework), and within reach of a hand already on WASD — which the four
     * arrow keys, the binds it exists to spare you in a fight, are not.
     *
     * <p>{@code X} is also read by {@code DebugInputHandler} as an axis key, but only while the model
     * debug editor is open, which is not a state a player is ever in.
     */
    public static final KeyMapping SPELL_WHEEL = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_wheel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    public static final KeyMapping SPELL_MENU = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".spell_menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping SKILL_MENU = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".skill_menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CHARACTER_SHEET);
        event.register(TOGGLE_STAT_HUD);
        event.register(SPELL_UP);
        event.register(SPELL_RIGHT);
        event.register(SPELL_DOWN);
        event.register(SPELL_LEFT);
        event.register(SPELL_WHEEL);
        event.register(SPELL_MENU);
        event.register(SKILL_MENU);
    }

    private SpellKeyBindings() {
    }
}
