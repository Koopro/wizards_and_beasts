package at.koopro.wizardsandbeasts.client.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
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
 *   <li>{@code ability_quick} — fire (or toggle) the pinned ability directly.</li>
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

    public static final KeyMapping ABILITY_QUICK = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".ability_quick",
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    private AbilityFrameworkKeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ABILITY_WHEEL);
        event.register(ABILITY_USE);
        event.register(ABILITY_QUICK);
    }
}
