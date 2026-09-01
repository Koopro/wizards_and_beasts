package at.koopro.wizardsandbeasts.client.map.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How one marker type is drawn and named.
 *
 * <p>Client resource, {@code assets/<ns>/map_marker_style/}. The server only ever sends a type id;
 * this is where that id becomes an icon, an ink colour, a legend entry and a name.
 *
 * <p>{@code size} exists so Hogwarts can be bigger than a fence post without a second code path.
 * The map's whole promise is that the castle is recognisable at a glance, and "at a glance" is a
 * statement about size before it is a statement about shape.
 */
public record MapMarkerStyle(
        /** Cell in {@code textures/gui/map/markers.png}. */
        int icon,
        /** ARGB ink. The marker sheet is authored greyscale and tinted here. */
        int tint,
        /**
         * Native size in sheet texels, scaled by the panel's own factor at draw time.
         *
         * <p>Everything this mod ships sets 16, the sheet's cell size, and lets the <em>art</em>
         * carry the hierarchy -- the castle fills its cell, a pin occupies the middle of one.
         * A value that is not 16 or a clean multiple of it is a non-integer resample of pixel
         * art, which is exactly what makes a symbol set look soft. The field stays because a
         * pack may ship its own sheet at another cell size.
         */
        int size,
        /** Translation key for the marker's name, when the marker itself carries no label. */
        String name,
        /** Legend group. Markers sharing a group collapse to one legend row. */
        String legend,
        /**
         * Draw the name on the parchment beside the icon, not only in the tooltip. Reserved for the
         * few places that earn permanent lettering; every marker labelled is a map you cannot read.
         */
        boolean labelled,
        /** Zoom below which this marker stops being drawn, so a zoomed-out map stays legible. */
        Optional<Double> minZoom
) {

    public static final Codec<MapMarkerStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 255).fieldOf("icon").forGetter(MapMarkerStyle::icon),
            MapBiomeStyle.COLOUR.optionalFieldOf("tint", 0xFF3A2E24).forGetter(MapMarkerStyle::tint),
            Codec.intRange(4, 64).optionalFieldOf("size", 16).forGetter(MapMarkerStyle::size),
            Codec.STRING.fieldOf("name").forGetter(MapMarkerStyle::name),
            Codec.STRING.optionalFieldOf("legend", "structure").forGetter(MapMarkerStyle::legend),
            Codec.BOOL.optionalFieldOf("labelled", false).forGetter(MapMarkerStyle::labelled),
            Codec.DOUBLE.optionalFieldOf("min_zoom").forGetter(MapMarkerStyle::minZoom)
    ).apply(instance, MapMarkerStyle::new));

    /** What an unknown marker type falls back to: a plain pin that still says where it is. */
    public static final MapMarkerStyle FALLBACK = new MapMarkerStyle(
            0, 0xFF3A2E24, 16, "map.wizards_and_beasts.marker.unknown", "waypoint", false,
            Optional.empty());
}
