package at.koopro.wizardsandbeasts.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * One 32x32-tile square of surveyed world — the unit the atlas stores, the network ships and
 * the client caches.
 *
 * <p>Two parallel arrays rather than one array of objects. A region is 1024 tiles and a world a
 * player has actually explored is hundreds of regions; an object per tile would be hundreds of
 * thousands of live objects describing two numbers each.
 *
 * <p>The biome array holds <em>palette indices</em>, not registry ids. The palette lives on the
 * {@link MapAtlas} and is what makes storage compact and, more importantly, what makes the format
 * survive a datapack change: a stored ordinal into a datapack-defined art table would silently
 * repaint the whole world the first time someone reordered their tile styles, whereas a palette of
 * biome ids resolves through the style table at draw time and falls back to the default tile for a
 * biome that no longer exists.
 *
 * <h2>Wire and disk format</h2>
 * Run-length encoded over the (biome, relief) pair. Surveyed terrain is overwhelmingly contiguous —
 * an ocean region is one run, a forest region a few dozen — so the 3KB raw form routinely lands
 * under 200 bytes, and a fully unsurveyed region is a single run. That matters because the
 * alternative is a per-chunk network message; regions are only affordable to ship whole because
 * they compress this well.
 */
public final class MapRegion {

    /** Palette index written for a tile no one has surveyed. */
    public static final short UNSURVEYED = -1;

    private final short[] biome;
    private final byte[] relief;
    /** Tiles with a biome index set. Kept incrementally so {@link #isEmpty} is not a scan. */
    private int surveyed;

    public MapRegion() {
        this.biome = new short[MapGeometry.TILES_PER_REGION_SQ];
        this.relief = new byte[MapGeometry.TILES_PER_REGION_SQ];
        Arrays.fill(this.biome, UNSURVEYED);
        Arrays.fill(this.relief, MapRelief.UNSURVEYED);
    }

    private MapRegion(short[] biome, byte[] relief) {
        this.biome = biome;
        this.relief = relief;
        for (short b : biome) {
            if (b != UNSURVEYED) surveyed++;
        }
    }

    /** Palette index at a tile, or {@link #UNSURVEYED}. Coordinates are absolute tile coords. */
    public short biomeAt(int tileX, int tileZ) {
        return biome[MapGeometry.tileIndex(tileX, tileZ)];
    }

    /** Relief at a tile, or {@code null} when unsurveyed. Coordinates are absolute tile coords. */
    public MapRelief reliefAt(int tileX, int tileZ) {
        return MapRelief.byId(relief[MapGeometry.tileIndex(tileX, tileZ)]);
    }

    public boolean isSurveyed(int tileX, int tileZ) {
        return biome[MapGeometry.tileIndex(tileX, tileZ)] != UNSURVEYED;
    }

    /** No tile in this region has been surveyed — nothing to store, nothing to send. */
    public boolean isEmpty() {
        return surveyed == 0;
    }

    public int surveyedCount() {
        return surveyed;
    }

    /**
     * Records a survey result.
     *
     * @return true when this changed the region, which is what the caller marks dirty on. A
     *         re-survey of an unchanged tile is the overwhelmingly common case once a player
     *         settles somewhere, and treating it as a write would keep the save file and the
     *         network permanently busy describing terrain that has not moved.
     */
    public boolean set(int tileX, int tileZ, short biomeIndex, MapRelief tileRelief) {
        int i = MapGeometry.tileIndex(tileX, tileZ);
        byte r = tileRelief.id();
        if (biome[i] == biomeIndex && relief[i] == r) {
            return false;
        }
        if (biome[i] == UNSURVEYED) {
            surveyed++;
        }
        biome[i] = biomeIndex;
        relief[i] = r;
        return true;
    }

    /** Copies every surveyed tile of {@code other} over this region. Used when merging a sync. */
    public void mergeFrom(MapRegion other) {
        for (int i = 0; i < biome.length; i++) {
            if (other.biome[i] == UNSURVEYED) continue;
            if (biome[i] == UNSURVEYED) surveyed++;
            biome[i] = other.biome[i];
            relief[i] = other.relief[i];
        }
    }

    // -- Run-length codec --------------------------------------------------

    /**
     * Encodes to the RLE byte form. Run lengths use a varint because the two cases that matter sit
     * at opposite ends of the range: a solid ocean region is one run of 1024, and a coastline is
     * hundreds of runs of 1 or 2.
     */
    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        int i = 0;
        while (i < biome.length) {
            short b = biome[i];
            byte r = relief[i];
            int run = 1;
            while (i + run < biome.length && biome[i + run] == b && relief[i + run] == r) {
                run++;
            }
            writeVarInt(out, run);
            out.write((b >> 8) & 0xFF);
            out.write(b & 0xFF);
            out.write(r & 0xFF);
            i += run;
        }
        return out.toByteArray();
    }

    /**
     * Decodes the RLE byte form, refusing anything that does not describe exactly one region.
     *
     * <p>A truncated or over-long payload is rejected rather than padded. This runs on data that
     * arrives over the network, and a decoder that quietly accepts a wrong length is a decoder that
     * can be handed a run count of two billion.
     */
    public static DataResult<MapRegion> decode(byte[] data) {
        short[] biome = new short[MapGeometry.TILES_PER_REGION_SQ];
        byte[] relief = new byte[MapGeometry.TILES_PER_REGION_SQ];
        Arrays.fill(biome, UNSURVEYED);
        Arrays.fill(relief, MapRelief.UNSURVEYED);
        int i = 0;
        int p = 0;
        while (p < data.length) {
            int shift = 0;
            long acc = 0;
            byte cur;
            do {
                if (p >= data.length || shift > 28) {
                    return DataResult.error(() -> "Malformed map-region run length");
                }
                cur = data[p++];
                acc |= (long) (cur & 0x7F) << shift;
                shift += 7;
            } while ((cur & 0x80) != 0);
            long run = acc;

            if (p + 3 > data.length) {
                return DataResult.error(() -> "Truncated map-region run body");
            }
            short b = (short) (((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF));
            byte r = data[p + 2];
            p += 3;

            if (run <= 0 || i + run > biome.length) {
                final long overflow = run;
                return DataResult.error(() -> "Map-region run of " + overflow + " overflows the region");
            }
            for (int k = 0; k < run; k++) {
                biome[i + k] = b;
                relief[i + k] = r;
            }
            i += (int) run;
        }
        if (i != biome.length) {
            final int got = i;
            return DataResult.error(() -> "Map region describes " + got + " of "
                    + MapGeometry.TILES_PER_REGION_SQ + " tiles");
        }
        return DataResult.success(new MapRegion(biome, relief));
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    /**
     * Disk form: the region's key alongside its Base64'd runs.
     *
     * <p>Base64 rather than a raw byte array because this rides inside a {@code SavedData} codec and
     * has to survive both NBT and, when someone dumps the save to JSON to debug it, a text ops. The
     * ~33% inflation is against a payload that RLE already shrank by an order of magnitude, and the
     * region file is gzipped on top.
     */
    public record Stored(long key, byte[] runs) {
        public static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("key").forGetter(Stored::key),
                ExtraCodecs.BASE64_STRING.fieldOf("runs").forGetter(Stored::runs)
        ).apply(instance, Stored::new));
    }

    public Stored store(long key) {
        return new Stored(key, encode());
    }
}
