package at.koopro.wizardsandbeasts.client.map.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * How one biome is drawn on the parchment.
 *
 * <p>A client resource, under {@code assets/<ns>/map_biome_style/}, not a datapack file. It is
 * entirely appearance — which cell of the tile sheet, what ink to tint it, whether the ink is dark
 * enough to need pale labels over it — and appearance belongs in a resource pack. It is also the
 * side that has to be available on a client connected to a vanilla server, which a datapack cannot
 * guarantee.
 *
 * <p>{@code biomes} is a list of biome ids rather than a tag for the same reason: tags are
 * server-side registry data and are not loaded from a resource pack. Matching is by exact id, with
 * {@code fallbackFor} covering the long tail — a pack declares one style for "any forest" and lists
 * the four it cares about naming, and anything unlisted lands on the default.
 */
public record MapBiomeStyle(
        List<Identifier> biomes,
        /** Cell in {@code textures/gui/map/tiles.png}, counted left-to-right, top-to-bottom. */
        int tile,
        /** ARGB ink multiplied into the tile sprite. The sheet is authored greyscale. */
        int tint,
        /** Ink for the water bands, when this biome has water in it. Falls back to the ocean ink. */
        Optional<Integer> waterTint,
        /**
         * Drawn on top of the land tile when the relief band earns it: the hachures a hill gets and
         * a field does not. Cell index, or empty to leave relief to the shared contour sheet.
         */
        Optional<Integer> reliefTile
) {

    /**
     * Hex colours as {@code "#RRGGBB"} rather than raw ints.
     *
     * <p>A resource pack author editing tile ink should not have to write {@code -3421236}, and JSON
     * has no unsigned int — the alpha bit makes every opaque colour negative, which is exactly the
     * sort of thing that gets "fixed" into transparency.
     */
    public static final Codec<Integer> COLOUR = Codec.STRING.comapFlatMap(
            text -> {
                String hex = text.startsWith("#") ? text.substring(1) : text;
                try {
                    return com.mojang.serialization.DataResult.success(
                            hex.length() == 8
                                    ? (int) Long.parseLong(hex, 16)
                                    : 0xFF000000 | Integer.parseInt(hex, 16));
                } catch (NumberFormatException e) {
                    return com.mojang.serialization.DataResult.error(
                            () -> "Not a #RRGGBB or #AARRGGBB colour: " + text);
                }
            },
            value -> String.format("#%08X", value));

    public static final Codec<MapBiomeStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("biomes").forGetter(MapBiomeStyle::biomes),
            Codec.intRange(0, 255).fieldOf("tile").forGetter(MapBiomeStyle::tile),
            COLOUR.fieldOf("tint").forGetter(MapBiomeStyle::tint),
            COLOUR.optionalFieldOf("water_tint").forGetter(MapBiomeStyle::waterTint),
            Codec.intRange(0, 255).optionalFieldOf("relief_tile").forGetter(MapBiomeStyle::reliefTile)
    ).apply(instance, MapBiomeStyle::new));
}
