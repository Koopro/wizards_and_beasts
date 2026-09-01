package at.koopro.wizardsandbeasts.map;

/**
 * How a surveyed tile sits relative to sea level — the second half of a tile's identity,
 * alongside its biome.
 *
 * <p>Biome alone cannot draw a legible map. A plains tile at y=64 and a plains tile on a y=140
 * ridge are the same biome and read as completely different country, and every river, coast
 * and lake in the world is a thin non-biome feature that a biome-only map loses entirely. Old
 * cartography solves exactly this problem with hachures and coastlines rather than with colour,
 * which is why relief is a first-class axis here and not a shading modifier.
 *
 * <p>Deliberately coarse. Seven bands is what a hand-drawn map can distinguish at a glance;
 * a continuous height would have to be quantised at draw time anyway, and storing it would
 * double the size of every region for detail no tile sprite can express.
 */
public enum MapRelief {
    /** Ocean floor well below sea level: open water, drawn darkest. */
    DEEP_WATER,
    /** Water within a few blocks of the surface: shallows, lakes, rivers. */
    WATER,
    /** Land whose surface is at or barely above sea level and borders water. */
    SHORE,
    /** Ordinary ground. */
    LOWLAND,
    /** Rolling ground: the first band that earns a contour line. */
    HILL,
    /** Serious elevation. */
    MOUNTAIN,
    /** Above the tree line. */
    PEAK;

    private static final MapRelief[] BY_ID = values();

    /** Sentinel written into a region's relief array for a tile nobody has surveyed yet. */
    public static final byte UNSURVEYED = -1;

    public byte id() {
        return (byte) ordinal();
    }

    /**
     * Decodes a stored relief byte, mapping anything unrecognised — {@link #UNSURVEYED}, or a
     * band written by a future version — onto {@code null} rather than throwing. A save from a
     * newer build must degrade to blank parchment, not crash the screen that opens it.
     */
    public static MapRelief byId(byte id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /** True for the two bands the renderer draws as water rather than as ground. */
    public boolean isWater() {
        return this == DEEP_WATER || this == WATER;
    }

    /**
     * Classifies a surveyed column.
     *
     * @param surfaceY   top of the world surface, water included
     * @param floorY     top of the ocean floor, water excluded
     * @param seaLevel   the level's own sea level, not a hardcoded 63 — the Nether, a superflat
     *                   and a datapack dimension all disagree about where "sea level" is
     * @param nearWater  whether any sampled point in this tile was water, which is what turns a
     *                   flat coastal tile into a {@link #SHORE} rather than a {@link #LOWLAND}
     */
    public static MapRelief classify(int surfaceY, int floorY, int seaLevel, boolean nearWater) {
        int depth = seaLevel - floorY;
        if (surfaceY > floorY) {
            return depth > 12 ? DEEP_WATER : WATER;
        }
        int above = floorY - seaLevel;
        if (above <= 2 && nearWater) return SHORE;
        if (above <= 16) return LOWLAND;
        if (above <= 40) return HILL;
        if (above <= 72) return MOUNTAIN;
        return PEAK;
    }
}
