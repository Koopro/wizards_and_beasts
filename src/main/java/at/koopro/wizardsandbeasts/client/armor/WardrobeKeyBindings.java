package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

/** Keybinds for worn gear. One so far: the hood. */
@NullMarked
public final class WardrobeKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wardrobe"));

    /**
     * Default: {@code H}. Free in vanilla and unused by every other bind in this mod — arrows /
     * {@code C} / {@code G} / {@code K} / {@code X} on the spell binds, {@code V} / {@code R} /
     * {@code N} / {@code M} / {@code B} on the ability framework.
     */
    public static final KeyMapping HOOD_TOGGLE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".hood_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private WardrobeKeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(HOOD_TOGGLE);
    }
}
