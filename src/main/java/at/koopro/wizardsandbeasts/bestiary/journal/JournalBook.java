package at.koopro.wizardsandbeasts.bestiary.journal;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Map;

/**
 * The journal's shared shape and per-species facts, held twice like {@code BestiaryEntryRegistry}: the server's copy
 * (layout from the datapack, facts derived from the other registries at sync time) and the client's (whatever the
 * server last synced). Kept apart so an integrated server and its own client never write each other's state.
 */
@NullMarked
public final class JournalBook {

    private static volatile JournalLayout serverLayout = JournalLayout.DEFAULT;
    private static volatile JournalLayout clientLayout = JournalLayout.DEFAULT;
    private static volatile Map<Identifier, JournalFacts> clientFacts = Map.of();

    private JournalBook() {}

    public static JournalLayout serverLayout() {
        return serverLayout;
    }

    public static JournalLayout clientLayout() {
        return clientLayout;
    }

    public static JournalFacts clientFacts(Identifier entryId) {
        return clientFacts.getOrDefault(entryId, JournalFacts.NONE);
    }

    public static void setClient(JournalLayout layout, Map<Identifier, JournalFacts> facts) {
        clientLayout = layout;
        clientFacts = Map.copyOf(facts);
    }

    /**
     * Loads {@code data/<ns>/bestiary_journal/layout.json}. The last namespace's file wins, so a datapack can
     * reshape the whole book; with none, {@link JournalLayout#DEFAULT}.
     */
    public static final class LayoutLoader extends SimpleJsonResourceReloadListener<JournalLayout> {
        private static final Logger LOGGER = LogUtils.getLogger();

        public LayoutLoader() {
            super(JournalLayout.CODEC, FileToIdConverter.json("bestiary_journal"));
        }

        @Override
        protected void apply(Map<Identifier, JournalLayout> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
            JournalLayout chosen = JournalLayout.DEFAULT;
            for (Map.Entry<Identifier, JournalLayout> e : loaded.entrySet()) {
                if (e.getKey().getPath().equals("layout")) {
                    chosen = e.getValue();
                }
            }
            serverLayout = chosen;
            LOGGER.info("Bestiary journal layout: {} section(s).", chosen.slots().size());
        }
    }
}
