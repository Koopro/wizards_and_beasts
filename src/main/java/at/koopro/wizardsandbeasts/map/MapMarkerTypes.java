package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The marker type ids this mod ships.
 *
 * <p>Ids only. There is no registry object behind them and no enum: a marker type is a string on
 * the wire, its art is a client resource under {@code assets/.../map_marker_style/}, and its
 * discovery rule is a datapack file under {@code data/.../map_discovery/}. A pack can add a type
 * this class has never heard of and it works; these constants exist so the mod's own code has one
 * spelling of each, not so the set is closed.
 *
 * <p>The wizarding ids are split from the generic ones on purpose. A castle-shaped structure marker
 * and Hogwarts are not the same thing, and the whole point of this map is that the second one is
 * recognisable at a glance.
 */
public final class MapMarkerTypes {

    private MapMarkerTypes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // -- Wizarding landmarks -----------------------------------------------

    /** The castle. Drawn larger than anything else on the parchment, and labelled. */
    public static final Identifier HOGWARTS = id("hogwarts");
    public static final Identifier HOGSMEADE = id("hogsmeade");
    public static final Identifier DIAGON_ALLEY = id("diagon_alley");
    public static final Identifier GRINGOTTS = id("gringotts");
    public static final Identifier MINISTRY = id("ministry");
    public static final Identifier AZKABAN = id("azkaban");
    public static final Identifier CHAMBER_OF_SECRETS = id("chamber_of_secrets");

    // -- Magical infrastructure --------------------------------------------

    /** A hearth on the Floo network. Comes from {@code FlooNetworkManager}, not from a chunk scan. */
    public static final Identifier FLOO_HEARTH = id("floo_hearth");
    /** One of the holder's own recorded Apparition destinations. */
    public static final Identifier APPARITION_POINT = id("apparition_point");
    /** Somewhere the map felt magic it could not name. */
    public static final Identifier MAGICAL_SITE = id("magical_site");

    // -- Ordinary geography ------------------------------------------------

    public static final Identifier VILLAGE = id("village");
    public static final Identifier STRUCTURE = id("structure");
    public static final Identifier FORTRESS = id("fortress");
    public static final Identifier RUIN = id("ruin");

    // -- The holder's own marks --------------------------------------------

    public static final Identifier DEATH = id("death");
    public static final Identifier HOME = id("home");
    public static final Identifier CAMP = id("camp");
    public static final Identifier SHOP = id("shop");
    public static final Identifier DANGER = id("danger");
    public static final Identifier CREATURE = id("creature");
    public static final Identifier TREASURE = id("treasure");
    /** The fallback pin, and what an unknown type falls back to on the client. */
    public static final Identifier WAYPOINT = id("waypoint");

    /**
     * The icons offered in the waypoint editor's type cycler, in the order they appear.
     *
     * <p>Only the player's own marks: cycling a pin onto {@link #HOGWARTS} would let anyone label
     * an empty field as the castle, and a legend whose symbols can mean anything explains nothing.
     */
    public static final List<Identifier> WAYPOINT_ICONS = List.of(
            WAYPOINT, HOME, CAMP, SHOP, TREASURE, DANGER, CREATURE, MAGICAL_SITE);
}
