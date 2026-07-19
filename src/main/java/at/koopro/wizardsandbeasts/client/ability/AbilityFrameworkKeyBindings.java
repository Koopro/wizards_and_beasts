package at.koopro.wizardsandbeasts.client.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The three ability-framework keybinds. All default <b>unbound</b> (matching the {@code ANIMAGUS_*}
 * convention) so they never collide with vanilla or the existing spell/ability binds (R/H/G/K/V/B/N/M…);
 * the player assigns them in Controls. Suggested binds: wheel = {@code V}-adjacent free key, use/quick as
 * desired.
 *
 * <ul>
 *   <li>{@code ability_wheel} — hold to open the radial, release to confirm the hovered entry.</li>
 *   <li>{@code ability_use} — fire the currently selected ACTIVE ability.</li>
 *   <li>{@code ability_quick_1..3} — fire (or toggle) the ability in that quick slot directly. Several
 *       abilities stay one-press, which is what makes moving instant combat binds onto the wheel viable.</li>
 * </ul>
 */
@NullMarked
public final class AbilityFrameworkKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "abilities"));

    public static final KeyMapping ABILITY_WHEEL = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".ability_wheel",
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping ABILITY_USE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".ability_use",
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    /** Indexed by quick-slot number; {@code QUICK_SLOTS[i]} fires slot {@code i}. */
    public static final KeyMapping[] QUICK_SLOTS = new KeyMapping[AbilitySelectionState.QUICK_SLOT_COUNT];

    static {
        for (int i = 0; i < QUICK_SLOTS.length; i++) {
            QUICK_SLOTS[i] = new KeyMapping(
                    "key." + WizardsAndBeastsMod.MODID + ".ability_quick_" + (i + 1),
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
