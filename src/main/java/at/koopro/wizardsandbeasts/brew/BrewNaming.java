package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.util.NamespaceMigration;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a brew id into the translation keys that name and describe it.
 *
 * <h2>Why derive the keys instead of reading the brew</h2>
 * <p>A {@link Brew} already carries a {@code displayName} key, so the obvious implementation is to look
 * the brew up and read it. That does not work where it is needed most. {@code Item.getName(ItemStack)}
 * runs on both sides, and the brew catalogue is datapack content: the server's copy lives in
 * {@link Brews}, filled by a reload listener, while the client has a <em>separate</em> store
 * ({@code ClientBrewData}) filled by a packet. Common code cannot touch the client one without dragging
 * a client-only class onto a dedicated server, and reading the server one from a client that never ran
 * a reload listener yields nothing. Every bottle would be called "Brew" on a real server and be named
 * correctly in single player — the exact class of bug that hides until someone else hosts.
 *
 * <p>Deriving the key from the id sidesteps all of it. It needs no registry, no packet and no side
 * check, and it is correct before {@code BrewDataSyncPayload} has even arrived.
 *
 * <h2>Why that is safe</h2>
 * <p>Because it is not a guess about a convention — it <em>is</em> the convention, unanimously. All
 * fourteen shipped brews declare exactly {@code brew.<namespace>.<path>.name} as their
 * {@code displayName}, and {@code BrewLangParityTest} fails the build if a new brew ever departs from
 * it. A datapack that insists on some other key still renders fine: it simply gets its own key honoured
 * by the systems that do read {@link Brew#displayName()} (the cauldron messages, the recipe viewer),
 * and the bottle falls back to the generic item name because the derived key is absent from the lang
 * file. Nothing breaks; the bottle is just less specific.
 *
 * <p>The description key has no counterpart on {@link Brew} at all. Brews carry {@code flavorText} as a
 * raw string, which means the flavour of every potion in the game is hard-coded English inside a
 * datapack and cannot be translated. Routing the tooltip through a key fixes that.
 */
@NullMarked
public final class BrewNaming {

    /** Fallback name for a bottle with no brew in it, or one naming a brew this client cannot resolve. */
    public static final String UNKNOWN_NAME_KEY = "item.wizards_and_beasts.brew";

    private BrewNaming() {}

    /** Translation key for the brew's name, e.g. {@code brew.wizards_and_beasts.felix_felicis.name}. */
    public static String nameKey(String brewId) {
        return key(brewId, "name");
    }

    /** Translation key for the brew's one-line flavour description. */
    public static String descKey(String brewId) {
        return key(brewId, "desc");
    }

    /**
     * {@code <namespace>:<path>} → {@code brew.<namespace>.<path>.<suffix>}.
     *
     * <p>Bare ids take the mod namespace, matching {@link Brews#byId} — the two have to agree, or a
     * recipe naming {@code "wiggenweld_potion"} would resolve to a brew whose bottle is then named from
     * a different key than the cauldron announced.
     */
    private static String key(String brewId, String suffix) {
        String id = NamespaceMigration.remapLegacyId(brewId);
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? WizardsAndBeastsMod.MODID : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return "brew." + namespace + "." + path + "." + suffix;
    }
}
