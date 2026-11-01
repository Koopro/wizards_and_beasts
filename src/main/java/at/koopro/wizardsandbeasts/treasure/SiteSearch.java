package at.koopro.wizardsandbeasts.treasure;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The nearest treasure site a chart may point to, as rules: vanilla's ring walk over a structure's placement grid
 * ({@code ChunkGenerator.findNearestMapStructure}, random-spread placements), with one difference — a site the
 * {@link TreasureSiteLedger} records as spent is walked past instead of returned. Pure over a {@link Probe}, so the
 * walk is tested without a world.
 *
 * <p>Vanilla's own {@code skipKnownStructures} flag was not the fix: it skips a site once any map has been
 * <em>drawn</em> to it (shared with the cartographer's maps), not once it has been looted. A charted tomb nobody
 * visited would be lost to every later expedition, and a looted one nobody charted would still be handed out.
 */
@NullMarked
public final class SiteSearch {

    private SiteSearch() {}

    /** A site that generates: its structure, the chunk its start is in, and where the chart's X goes. */
    public record Site(String structure, int startX, int startZ, int locateX, int locateZ) {

        /** The ledger's key: one structure's start is one site. */
        public String key() {
            return structure + "@" + startX + "," + startZ;
        }
    }

    /** One placement grid (a structure set): its spacing in chunks, and which of the tag's structures share it. */
    public record Grid(int spacing) {}

    /** Asks the world what generates at one cell of a grid. */
    @FunctionalInterface
    public interface Probe {
        /** The site generating in the potential chunk of cell ({@code cellX}, {@code cellZ}), or null for none. */
        @Nullable Site at(int grid, int cellX, int cellZ);
    }

    /**
     * {@link #nearest} one cell at a time, for a search too dear to run in one tick. Each {@link #step} asks the world
     * about a single cell; between steps the walk is only its own position.
     */
    public static final class Walk {
        private final int originX;
        private final int originZ;
        private final int spacing;
        private final int radius;
        private final Probe probe;
        private int ring;
        private int dx;
        private int dz;
        private @Nullable Site best;
        private long bestDist = Long.MAX_VALUE;
        private boolean finished;

        /** Walks one grid of {@code spacing} chunks. */
        public Walk(int originChunkX, int originChunkZ, int spacing, int radius, Probe probe) {
            this.originX = originChunkX;
            this.originZ = originChunkZ;
            this.spacing = spacing;
            this.radius = radius;
            this.probe = probe;
        }

        public boolean finished() {
            return finished;
        }

        /** The nearest site, once {@link #finished}; null when none lies in reach. */
        public @Nullable Site found() {
            return best;
        }

        /** Asks about the next cell. A ring is always finished, so what is found is the nearest of its ring. */
        public void step() {
            if (finished) {
                return;
            }
            Site site = probe.at(0, originX + spacing * (dx - ring), originZ + spacing * (dz - ring));
            if (site != null) {
                long ddx = site.locateX() - ((long) originX * 16 + 8);
                long ddz = site.locateZ() - ((long) originZ * 16 + 8);
                long dist = ddx * ddx + ddz * ddz;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = site;
                }
            }
            advance();
        }

        private void advance() {
            int side = 2 * ring;
            do {
                dz++;
                if (dz > side) {
                    dz = 0;
                    dx++;
                }
            } while (dx <= side && dx != 0 && dx != side && dz != 0 && dz != side);
            if (dx > side) {
                if (best != null || ring >= radius) {
                    finished = true;
                    return;
                }
                ring++;
                dx = 0;
                dz = 0;
            }
        }
    }

    /**
     * Walks rings outward from {@code originChunk}, each grid's cells on the ring's edge, and returns the nearest
     * site that is not spent within the first ring holding one. Null when every site within {@code radius} rings is
     * spent or none generates; the walk always ends, after at most {@code (2r+1)^2} probes per grid.
     */
    public static @Nullable Site nearest(int originChunkX, int originChunkZ, List<Grid> grids, int radius, Probe probe,
                                         Predicate<Site> spent) {
        long originX = (long) originChunkX * 16 + 8;
        long originZ = (long) originChunkZ * 16 + 8;
        for (int ring = 0; ring <= radius; ring++) {
            Site best = null;
            long bestDist = Long.MAX_VALUE;
            for (int g = 0; g < grids.size(); g++) {
                int spacing = grids.get(g).spacing();
                for (int dx = -ring; dx <= ring; dx++) {
                    boolean edgeX = dx == -ring || dx == ring;
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (!edgeX && dz != -ring && dz != ring) {
                            continue;
                        }
                        Site site = probe.at(g, originChunkX + spacing * dx, originChunkZ + spacing * dz);
                        if (site == null || spent.test(site)) {
                            continue;
                        }
                        long ddx = site.locateX() - originX;
                        long ddz = site.locateZ() - originZ;
                        long dist = ddx * ddx + ddz * ddz;
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = site;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
