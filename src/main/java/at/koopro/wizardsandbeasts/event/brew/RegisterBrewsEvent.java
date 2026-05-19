package at.koopro.wizardsandbeasts.event.brew;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Mod-bus event fired during common setup that lets <strong>third-party mods</strong>
 * contribute brews and brewing recipes without touching WizardsAndBeastsMod source.
 *
 * <p>Fires <em>after</em> the initial datapack reload and re-fires on every
 * subsequent reload, so addon contributions survive {@code /reload}. Mirrors
 * {@link RegisterSpellsEvent}.
 *
 * <p>Usage from an addon mod:
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegisterBrews(RegisterBrewsEvent event) {
 *     event.register(new Brew(...));
 *     event.register(new BrewingRecipe(...));
 * }
 * }</pre>
 */
public final class RegisterBrewsEvent extends Event implements IModBusEvent {

    public RegisterBrewsEvent() {
    }

    public Brew register(Brew brew) {
        return Brews.register(brew);
    }

    public BrewingRecipe register(BrewingRecipe recipe) {
        return BrewingRecipes.register(recipe);
    }
}
