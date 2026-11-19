package at.koopro.wizardsandbeasts.client.atmosphere;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Knows, without asking the server, what kind of place the camera is in.
 *
 * <p>The client is told nothing about structures or locations (only Diagon Alley and the extension realm are knowable,
 * by dimension), and a library a player builds is as much a library as one the mod places. So places are recognised
 * the way a person would: by looking round. Each tick a fixed handful of random blocks within {@link #REACH} of the
 * camera are read and tested against every profile's block tags; the hits feed each place's {@link PlaceScores}.
 * The cost is {@link #SAMPLES} block reads a tick, whatever the world looks like; nothing is scanned.
 *
 * <p>Every hit also lands in the place's small ring of <em>anchors</em>: recent positions of its blocks, from which its
 * emitters and sounds start (dust rises off the shelves the sampler actually saw). Render thread only.
 */
@NullMarked
public final class PlaceSense {

    /** Block reads per tick. */
    static final int SAMPLES = 32;
    /** How far round the camera samples land, horizontally. */
    static final int REACH = 10;
    /** How far up and down. */
    static final int REACH_Y = 5;
    /** Anchors remembered per place. */
    static final int ANCHORS = 24;

    /** One recognised kind of place and its running state. */
    public static final class Place {
        final Identifier id;
        final AtmosphereProfile profile;
        final PlaceScores scores;
        private final long[] anchors = new long[ANCHORS];
        private int anchorCount;
        private int anchorNext;
        /** Fractional particles carried to the next tick, per emitter. */
        final float[] carry;
        /** Ticks to the next play, per sound cue; below zero means not yet scheduled. */
        final int[] soundIn;
        @Nullable AmbientBed bed;
        /** Whether its gates (dimension, time, weather, sky, a required biome) held this tick. */
        boolean gated;
        /** Whether the camera stood in its biome this tick. */
        boolean inBiome;

        Place(Identifier id, AtmosphereProfile profile) {
            this.id = id;
            this.profile = profile;
            this.scores = new PlaceScores(profile.enter(), profile.leave(), profile.full());
            this.carry = new float[profile.emitters().size()];
            this.soundIn = new int[profile.sounds().size()];
            java.util.Arrays.fill(soundIn, -1);
        }

        void anchor(BlockPos pos) {
            anchors[anchorNext] = pos.asLong();
            anchorNext = (anchorNext + 1) % ANCHORS;
            anchorCount = Math.min(ANCHORS, anchorCount + 1);
        }

        /** A recent anchor, or {@code null} if none has been seen yet. */
        @Nullable BlockPos anyAnchor(RandomSource random) {
            return anchorCount == 0 ? null : BlockPos.of(anchors[random.nextInt(anchorCount)]);
        }

        void forget() {
            scores.reset();
            anchorCount = 0;
            anchorNext = 0;
            java.util.Arrays.fill(carry, 0f);
            java.util.Arrays.fill(soundIn, -1);
        }

        public Identifier id() {
            return id;
        }

        public AtmosphereProfile profile() {
            return profile;
        }

        public float strength() {
            return scores.strength();
        }

        public double score() {
            return scores.score();
        }

        public boolean active() {
            return scores.active();
        }

        public int anchorCount() {
            return anchorCount;
        }
    }

    private static List<Place> places = List.of();

    private PlaceSense() {}

    /** Replaces the profiles (resource reload). Running beds are left to fade out on their own. */
    static void load(Map<Identifier, AtmosphereProfile> profiles) {
        for (Place old : places) {
            if (old.bed != null) {
                old.bed.release();
            }
        }
        List<Place> next = new ArrayList<>();
        profiles.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(e -> next.add(new Place(e.getKey(), e.getValue())));
        places = List.copyOf(next);
    }

    public static List<Place> places() {
        return places;
    }

    /** Forgets every place at once: a new level, or atmosphere switched off. */
    static void reset() {
        for (Place p : places) {
            p.forget();
        }
    }

    /** One tick of looking round. */
    static void tick(ClientLevel level, Entity camera, AtmosphereProfile.Ambience ambience, RandomSource random) {
        List<Place> all = places;
        if (all.isEmpty()) {
            return;
        }
        BlockPos eye = BlockPos.containing(camera.getEyePosition());
        double[] hits = new double[all.size()];
        boolean anyGated = false;
        var biome = level.getBiome(eye);
        for (int p = 0; p < all.size(); p++) {
            Place place = all.get(p);
            AtmosphereProfile prof = place.profile;
            place.inBiome = prof.biome().isPresent() && biome.is(prof.biome().get());
            place.gated = prof.when().test(ambience)
                    && prof.dimension().map(d -> d.equals(level.dimension())).orElse(true)
                    && (!prof.biomeRequired() || place.inBiome);
            anyGated |= place.gated && !prof.blocks().isEmpty();
        }
        int solid = 0;
        if (anyGated) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < SAMPLES; i++) {
                pos.set(eye.getX() + random.nextInt(REACH * 2 + 1) - REACH,
                        eye.getY() + random.nextInt(REACH_Y * 2 + 1) - REACH_Y,
                        eye.getZ() + random.nextInt(REACH * 2 + 1) - REACH);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                solid++;
                for (int p = 0; p < all.size(); p++) {
                    Place place = all.get(p);
                    if (!place.gated) {
                        continue;
                    }
                    for (AtmosphereProfile.BlockRule rule : place.profile.blocks()) {
                        if (state.is(rule.tag())) {
                            hits[p] += rule.weight();
                            place.anchor(pos);
                            break;
                        }
                    }
                }
            }
        }
        // Share of what is there, not of the sample box: a library reads as one whether the room is small or large.
        // In open air the floor keeps a handful of stray hits from reading as a whole place.
        double denominator = Math.max(solid, SAMPLES / 4.0);
        for (int p = 0; p < all.size(); p++) {
            Place place = all.get(p);
            AtmosphereProfile prof = place.profile;
            double frame = 0;
            if (place.gated) {
                frame = hits[p] / denominator;
                if (place.inBiome) {
                    frame += prof.biomeWeight();
                }
                if (prof.blocks().isEmpty() && prof.biome().isEmpty()) {
                    // Known by its dimension alone.
                    frame = prof.full();
                }
            }
            place.scores.observe(frame);
        }
    }
}
