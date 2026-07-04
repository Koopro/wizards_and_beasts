package at.koopro.wizardsandbeasts.handbook;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side datapack reload listener for {@link HandbookChapter}s
 * ({@code data/<ns>/handbook/chapters/<id>.json}), mirroring {@code BestiaryEntryLoader}.
 *
 * <p>Chapters are sorted by {@code sort_index} (ascending, then id) on load. The full sorted list is
 * pushed to clients via {@code SyncHandbookPayload}; clients store it in {@link #CLIENT_CHAPTERS}.
 */
public final class HandbookChapterManager extends SimpleJsonResourceReloadListener<HandbookChapter> {
    public static final String DIRECTORY = "handbook/chapters";

    private static final Map<Identifier, HandbookChapter> SERVER = new LinkedHashMap<>();

    /** Client-side cache, populated on {@code SyncHandbookPayload} receipt. Read only on the client. */
    public static volatile List<HandbookChapter> CLIENT_CHAPTERS = List.of();

    public HandbookChapterManager() {
        super(HandbookChapter.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, HandbookChapter> jsonChapters, ResourceManager resourceManager, ProfilerFiller profiler) {
        SERVER.clear();
        jsonChapters.values().stream()
                .sorted(sortOrder())
                .forEach(chapter -> SERVER.put(chapter.id(), chapter));
    }

    private static Comparator<HandbookChapter> sortOrder() {
        return Comparator.comparingInt(HandbookChapter::sortIndex)
                .thenComparing(chapter -> chapter.id().toString());
    }

    /** Server-side snapshot of all loaded chapters, in sorted order. */
    public static List<HandbookChapter> serverChapters() {
        return List.copyOf(SERVER.values());
    }

    /** Replace the client cache with the synced list, re-sorted defensively. */
    public static void setClientChapters(List<HandbookChapter> chapters) {
        List<HandbookChapter> sorted = new ArrayList<>(chapters);
        sorted.sort(sortOrder());
        CLIENT_CHAPTERS = List.copyOf(sorted);
    }
}
