package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The kinds of case the Auror Office posts against non-player culprits, authored in
 * {@code data/<namespace>/ministry_cases/<id>.json}.
 *
 * <p>A template names the culprit's entity type, what they did, how grave it was, how much the Office pays for
 * bringing them in alive, and the Auror-track rank (by index) an Auror needs to be trusted with it. Adding a kind
 * of case is a JSON file.
 */
@NullMarked
public final class NpcCaseTemplates {

    /**
     * @param culprit    the entity type the culprit is spawned as
     * @param spell      the magic used, for the case file
     * @param legalClass how grave it was
     * @param bountyKnuts what the Office pays for the culprit taken alive
     * @param minRank    Auror-track rank index needed to take it; 0 for any Auror
     * @param weight     how often it is posted, relative to the others
     */
    public record Template(Identifier culprit, String spell, LegalClass legalClass, long bountyKnuts, int minRank,
                           int weight) {

        public static final Codec<Template> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("culprit").forGetter(Template::culprit),
                Codec.STRING.fieldOf("spell").forGetter(Template::spell),
                LegalClass.CODEC.fieldOf("legal_class").forGetter(Template::legalClass),
                Codec.LONG.optionalFieldOf("bounty_knuts", 0L).forGetter(Template::bountyKnuts),
                Codec.intRange(0, 31).optionalFieldOf("min_rank", 0).forGetter(Template::minRank),
                Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(Template::weight)
        ).apply(instance, Template::new));
    }

    private static volatile Map<Identifier, Template> TEMPLATES = Map.of();

    private NpcCaseTemplates() {}

    public static void replaceAll(Map<Identifier, Template> templates) {
        TEMPLATES = Map.copyOf(templates);
    }

    public static Map<Identifier, Template> all() {
        return TEMPLATES;
    }

    public static @Nullable Template get(Identifier id) {
        return TEMPLATES.get(id);
    }

    /** A weighted draw among the templates an Auror of {@code rank} may take, or {@code null} when there are none. */
    public static Map.@Nullable Entry<Identifier, Template> draw(RandomSource random, int rank) {
        List<Map.Entry<Identifier, Template>> open = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Identifier, Template> entry : TEMPLATES.entrySet()) {
            if (entry.getValue().minRank() <= rank) {
                open.add(entry);
                total += entry.getValue().weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        // Sorted so the draw is stable for a given random value, whatever order the map iterates in.
        open.sort(Map.Entry.comparingByKey());
        int roll = random.nextInt(total);
        for (Map.Entry<Identifier, Template> entry : open) {
            roll -= entry.getValue().weight();
            if (roll < 0) {
                return entry;
            }
        }
        return open.getLast();
    }

    /** Loads {@code ministry_cases/}. Wired from the mod's server reload listeners. */
    public static final class Loader extends SimpleJsonResourceReloadListener<Template> {

        private static final Logger LOGGER = LogUtils.getLogger();

        public static final String DIRECTORY = "ministry_cases";

        public Loader() {
            super(Template.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, Template> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            replaceAll(entries);
            LOGGER.info("[W&B] Loaded {} Auror Office case templates.", entries.size());
        }
    }
}
