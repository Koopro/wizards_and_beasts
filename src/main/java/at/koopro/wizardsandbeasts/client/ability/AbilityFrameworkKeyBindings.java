package at.koopro.wizardsandbeasts.client.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

/**
 * The ability-framework keybinds.
 *
 * <ul>
 *   <li>{@code ability_wheel} ({@code V}) — hold to open the radial, release to confirm the hovered entry.</li>
 *   <li>{@code ability_use} ({@code R}) — fire the currently selected ACTIVE ability.</li>
 *   <li>{@code ability_quick_1..3} ({@code N}, {@code M}, {@code B}) — fire (or toggle) the ability in that
 *       quick slot directly. Several abilities stay one-press, which is what makes moving instant combat
 *       binds onto the wheel viable.</li>
 * </ul>
 *
 * <p>The defaults deliberately reuse the keys freed by the keybind migration, each keeping roughly its old
 * meaning: {@code R} fired Apparition and now fires the armed ability; {@code V} toggled the obscurus form
 * and now opens the wheel that toggles it; {@code N}/{@code M} were the two instant Obscurial attacks and are
 * now the two instant quick slots; {@code B} was the stress vent. None collide with vanilla or with the
 * remaining mod binds (arrows / {@code G} / {@code K}), so an Obscurial player is combat-ready without
 * visiting Controls.
 *
 * <p><b>Caveat:</b> {@code ability_wheel} and {@code ability_use} shipped unbound in the previous build, so a
 * client that already launched it has them saved as unbound in {@code options.txt} and will keep that —
 * defaults only apply to key names the options file has never seen. The three quick-slot names are new and
 * do pick their defaults up.
 */
@NullMarked
public final class AbilityFrameworkKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "abilities"));

    /** Default key per quick slot, indexed by slot — the two instant Obscurial attack keys, then the vent key. */
    private static final int[] QUICK_SLOT_DEFAULTS = {GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_B};

    public static final KeyMapping ABILITY_WHEEL = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".ability_wheel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    public static final KeyMapping ABILITY_USE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".ability_use",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** Indexed by quick-slot number; {@code QUICK_SLOTS[i]} fires slot {@code i}. */
    public static final KeyMapping[] QUICK_SLOTS = new KeyMapping[AbilitySelectionState.QUICK_SLOT_COUNT];

    static {
        for (int i = 0; i < QUICK_SLOTS.length; i++) {
            // Slots beyond the defaults table ship unbound rather than guessing at a free key.
            QUICK_SLOTS[i] = i < QUICK_SLOT_DEFAULTS.length
                    ? new KeyMapping("key." + WizardsAndBeastsMod.MODID + ".ability_quick_" + (i + 1),
                            InputConstants.Type.KEYSYM, QUICK_SLOT_DEFAULTS[i], CATEGORY)
                    : new KeyMapping("key." + WizardsAndBeastsMod.MODID + ".ability_quick_" + (i + 1),
                            InputConstants.UNKNOWN.getValue(), CATEGORY);
        }
    }

    private AbilityFrameworkKeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ABILITY_WHEEL);
        event.register(ABILITY_USE);
        for (KeyMapping quickSlot : QUICK_SLOTS) {
            event.register(quickSlot);
        }
    }
}
