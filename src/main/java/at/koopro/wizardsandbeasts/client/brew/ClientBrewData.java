package at.koopro.wizardsandbeasts.client.brew;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.network.brew.BrewDataSyncPayload;
import com.mojang.logging.LogUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The client's copy of the server's brewing content, populated by {@link BrewDataSyncPayload}.
 *
 * <p>Deliberately separate from the server-side {@code Brews} / {@code BrewingRecipes} tables rather than
 * writing into them. Those are filled by datapack reload listeners and are the server's truth; in single
 * player the client shares the same JVM, and having the network path write into the same statics would
 * mean a sync could race the reload that produced it. A distinct store makes "what the client was told"
 * and "what the server loaded" separately inspectable, which is also what makes the single-player and
 * dedicated-server cases behave identically instead of only appearing to.
 *
 * <p>Published by swapping a {@code volatile} reference to an immutable snapshot, per the project's
 * reload convention. Readers are recipe-viewer code that may run on the render thread while a sync lands.
 */
@NullMarked
public final class ClientBrewData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record Snapshot(Map<String, Brew> brews, List<BrewingRecipe> recipes) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), List.of());
    }

    private static volatile Snapshot current = Snapshot.EMPTY;

    /** Run after every applied sync, so a viewer can rebuild its categories. */
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientBrewData() {}

    public static void whenApplied(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static List<BrewingRecipe> recipes() {
        return current.recipes();
    }

    public static @Nullable Brew brew(String id) {
        return current.brews().get(id);
    }

    public static void accept(BrewDataSyncPayload payload) {
        Map<String, Brew> brews = new LinkedHashMap<>();
        payload.brews().forEach((id, definition) -> {
            Brew brew = definition.toBrew(id);
            if (brew != null) {
                brews.put(id, brew);
            }
        });

        List<BrewingRecipe> recipes = payload.recipes().entrySet().stream()
                .map(entry -> entry.getValue().toRecipe(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();

        current = new Snapshot(Map.copyOf(brews), List.copyOf(recipes));
        LOGGER.debug("Client brew data: {} brews, {} recipes", brews.size(), recipes.size());
        LISTENERS.forEach(Runnable::run);
    }

    /**
     * Drops everything. Called on disconnect: this is server-owned content, and holding a previous
     * server's brews while connecting to another one would show a recipe viewer entries that do not exist.
     */
    public static void clear() {
        current = Snapshot.EMPTY;
        LISTENERS.forEach(Runnable::run);
    }
}
