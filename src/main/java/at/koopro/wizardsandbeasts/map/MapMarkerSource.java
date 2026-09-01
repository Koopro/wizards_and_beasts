package at.koopro.wizardsandbeasts.map;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Who put a marker on the map, which is what decides whether a player may edit or delete it.
 *
 * <p>Kept separate from the marker's <em>type</em>: a castle icon can be a landmark the map found
 * or a pin a player placed and named "castle", and only one of the two should vanish when they
 * press delete. Folding the two axes together is how a waypoint system ends up either letting
 * players erase discovered geography or refusing to let them tidy their own pins.
 */
public enum MapMarkerSource implements StringRepresentable {
    /** The map found it: a structure, a landmark, a registered hearth. Not player-editable. */
    DISCOVERY("discovery"),
    /** A player pushed a pin in. Fully editable by its owner. */
    WAYPOINT("waypoint"),
    /** Where the owner last died. Replaced on each death, cleared when they collect. */
    DEATH("death");

    public static final Codec<MapMarkerSource> CODEC = StringRepresentable.fromEnum(MapMarkerSource::values);

    private final String name;

    MapMarkerSource(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Discoveries are the map's own record of the world and are not a player's to rewrite. */
    public boolean playerEditable() {
        return this != DISCOVERY;
    }
}
