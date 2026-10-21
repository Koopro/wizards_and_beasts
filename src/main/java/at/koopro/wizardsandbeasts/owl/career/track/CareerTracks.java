package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.owl.Profession;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The loaded career tracks, one per profession at most: {@code career_tracks/magizoologist.json} is the
 * Magizoologist's. The file name must be a {@link Profession} name; any other file, or a track that fails
 * {@link CareerTrack#validate()}, is refused with a log line and that career keeps its flat wage.
 */
@NullMarked
public final class CareerTracks {

    public static final String DIRECTORY = "career_tracks";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Profession, CareerTrack> tracks = Map.of();

    private CareerTracks() {}

    public static @Nullable CareerTrack get(@Nullable Profession profession) {
        return profession == null ? null : tracks.get(profession);
    }

    /** Replaces every track at once (reload, tests). */
    public static void replaceAll(Map<Profession, CareerTrack> loaded) {
        tracks = Map.copyOf(loaded);
    }

    /** Reads one file's profession from its id; {@code null} when it names none. */
    static @Nullable Profession professionOf(Identifier fileId) {
        try {
            return Profession.valueOf(fileId.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<CareerTrack> {

        public Loader() {
            super(CareerTrack.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, CareerTrack> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<Profession, CareerTrack> loaded = new EnumMap<>(Profession.class);
            entries.forEach((id, track) -> {
                Profession profession = professionOf(id);
                if (profession == null || !profession.offered()) {
                    LOGGER.error("[Career] {} names no offered profession; ignored", id);
                    return;
                }
                List<String> problems = track.validate();
                if (!problems.isEmpty()) {
                    LOGGER.error("[Career] track {} refused: {}", id, problems);
                    return;
                }
                loaded.put(profession, track);
            });
            replaceAll(loaded);
            LOGGER.info("[Career] Loaded {} career track(s): {}", loaded.size(), loaded.keySet());
        }
    }
}
