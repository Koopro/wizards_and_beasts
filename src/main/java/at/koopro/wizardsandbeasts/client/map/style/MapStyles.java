package at.koopro.wizardsandbeasts.client.map.style;

import at.koopro.wizardsandbeasts.map.MapRelief;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The loaded biome and marker styles, and the small amount of logic that turns a stored tile into
 * a sprite cell and an ink colour.
 *
 * <p>Resolution is a plain map lookup with one fallback, and the fallback matters more than the
 * lookup: a client whose resource pack has never heard of a biome another mod added must still draw
 * that tile as *something*, because the alternative is a hole in the parchment exactly where the
 * player explored. Unknown biomes land on {@link #DEFAULT_STYLE}, which reads as unremarkable
 * country rather than as an error.
 */
public final class MapStyles {

    /**
     * What an unrecognised biome looks like: plain ground, tile 0, in the map's mid ink.
     *
     * <p>Deliberately not magenta-and-black. A missing texture should shout; a biome from a mod the
     * pack author has not styled yet should not, because it is the normal state of a modded world
     * and the map still has to be usable in it.
     */
    public static final MapBiomeStyle DEFAULT_STYLE = new MapBiomeStyle(
            List.of(), 0, 0xFF9C8F66, java.util.Optional.empty(), java.util.Optional.empty());

    private static volatile Map<Identifier, MapBiomeStyle> biomeStyles = Map.of();
    private static volatile Map<Identifier, MapMarkerStyle> markerStyles = Map.of();

    private MapStyles() {
    }

    static void replaceBiomeStyles(Map<Identifier, MapBiomeStyle> loaded) {
        // Flattened from "one file, many biomes" to "one biome, one style" at load time: the draw
        // loop asks this question once per visible tile, and a linear scan of every style's biome
        // list per tile is the difference between a map that opens and a map that stutters.
        Map<Identifier, MapBiomeStyle> byBiome = new HashMap<>();
        for (MapBiomeStyle style : loaded.values()) {
            for (Identifier biome : style.biomes()) {
                byBiome.put(biome, style);
            }
        }
        biomeStyles = Map.copyOf(byBiome);
    }

    static void replaceMarkerStyles(Map<Identifier, MapMarkerStyle> loaded) {
        markerStyles = Map.copyOf(loaded);
    }

    public static MapBiomeStyle biome(Identifier biome) {
        MapBiomeStyle style = biomeStyles.get(biome);
        return style != null ? style : DEFAULT_STYLE;
    }

    public static MapMarkerStyle marker(Identifier type) {
        MapMarkerStyle style = markerStyles.get(type);
        return style != null ? style : MapMarkerStyle.FALLBACK;
    }

    public static int biomeStyleCount() {
        return biomeStyles.size();
    }

    public static int markerStyleCount() {
        return markerStyles.size();
    }

    // -- Tile resolution ---------------------------------------------------

    /**
     * The sprite cell for a tile.
     *
     * <p>Water overrides the biome's land tile because a river through a forest is a river: the
     * biome there is still {@code forest}, and drawing tree clusters over the water is what makes a
     * biome-coloured map unreadable at every coastline. Relief above {@code LOWLAND} likewise takes
     * over, so a mountain in a jungle draws as a mountain.
     */
    public static int tileFor(MapBiomeStyle style, MapRelief relief) {
        if (relief == null) {
            return TILE_UNKNOWN;
        }
        return switch (relief) {
            case DEEP_WATER -> TILE_DEEP_WATER;
            case WATER -> TILE_WATER;
            case SHORE -> TILE_SHORE;
            case LOWLAND -> style.tile();
            case HILL -> style.reliefTile().orElse(TILE_HILL);
            case MOUNTAIN -> TILE_MOUNTAIN;
            case PEAK -> TILE_PEAK;
        };
    }

    /** The ink for a tile: the biome's, or its water ink where the tile is water. */
    public static int tintFor(MapBiomeStyle style, MapRelief relief) {
        if (relief != null && relief.isWater()) {
            int water = style.waterTint().orElse(WATER_INK);
            return relief == MapRelief.DEEP_WATER ? darken(water, 0.78F) : water;
        }
        if (relief == MapRelief.PEAK) {
            return PEAK_INK;
        }
        return style.tint();
    }

    /** Multiplies the RGB of an ARGB colour, leaving alpha alone. */
    public static int darken(int argb, float factor) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // -- Shared cells, for terrain no biome style owns ----------------------

    /** Blank parchment: charted, but nothing recognised. */
    public static final int TILE_UNKNOWN = 0;
    public static final int TILE_DEEP_WATER = 1;
    public static final int TILE_WATER = 2;
    public static final int TILE_SHORE = 3;
    public static final int TILE_HILL = 4;
    public static final int TILE_MOUNTAIN = 5;
    public static final int TILE_PEAK = 6;

    /** The map's water ink, used when a biome style does not name its own. */
    public static final int WATER_INK = 0xFF6E8CA0;
    /** Snowline: above the tree line everything is the same pale wash regardless of biome. */
    public static final int PEAK_INK = 0xFFE4DECB;
}
