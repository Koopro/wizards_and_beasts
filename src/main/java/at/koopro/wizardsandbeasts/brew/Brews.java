package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.util.NamespaceMigration;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory registry of {@link Brew}s. Mirrors {@link at.koopro.wizardsandbeasts.spell.Spells}.
 *
 * <p>Three contribution paths:
 * <ul>
 *   <li><b>Datapack JSON</b> via
 *       {@link at.koopro.wizardsandbeasts.brew.def.BrewReloadListener} — the primary path.
 *       Cleared and rebuilt on every {@code /reload}.</li>
 *   <li><b>Addon mods</b> via {@link #register(Brew)} from inside
 *       {@link at.koopro.wizardsandbeasts.event.RegisterBrewsEvent}.</li>
 *   <li><b>Tests</b> via {@link #register(Brew)} directly.</li>
 * </ul>
 *
 * <p>Brews resolved by {@code byId} accept both bare ids ({@code "wiggenweld_potion"})
 * and namespaced ids ({@code "wizards_and_beasts:wiggenweld_potion"}); bare ids fall back to
 * the mod id namespace, matching the {@code Spells.byId} convention.
 */
public final class Brews {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Brew> BY_ID = new LinkedHashMap<>();

    private Brews() {}

    public static Brew register(Brew brew) {
        if (BY_ID.containsKey(brew.id())) {
            LOGGER.warn("Brew id collision: '{}' is already registered; replacing.", brew.id());
        }
        BY_ID.put(brew.id(), brew);
        return brew;
    }

    /** Removes every entry. Used by {@link at.koopro.wizardsandbeasts.brew.def.BrewReloadListener}. */
    public static void clear() {
        BY_ID.clear();
    }

    @Nullable
    public static Brew byId(String id) {
        if (id == null) return null;
        id = NamespaceMigration.remapLegacyId(id);
        Brew direct = BY_ID.get(id);
        if (direct != null) return direct;
        if (!id.contains(":")) {
            return BY_ID.get(WizardsAndBeastsMod.MODID + ":" + id);
        }
        return null;
    }

    public static Collection<Brew> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static int count() {
        return BY_ID.size();
    }
}
