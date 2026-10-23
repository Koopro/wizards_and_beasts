package at.koopro.wizardsandbeasts.brew.knowledge;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Where brewing recipes are learned from, as data: {@code brew_recipe_sources/<id>.json} lists recipe ids.
 *
 * <ul>
 *   <li>{@code basic} -- known by everyone from the start (the first-year potions).</li>
 *   <li>One file per textbook, named after the book's item id ({@code magical_draughts_and_potions}, ...): what
 *       reading that book teaches.</li>
 *   <li>{@code apothecary_lessons} -- what the apothecary's Potions lessons teach, one per lesson, in order.</li>
 *   <li>{@code recipe_pages} -- the pool a loose recipe page found in the world is written from.</li>
 * </ul>
 */
@NullMarked
public final class RecipeSources {

    public static final String DIRECTORY = "brew_recipe_sources";
    public static final Identifier BASIC = id("basic");
    public static final Identifier LESSONS = id("apothecary_lessons");
    public static final Identifier PAGES = id("recipe_pages");
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Source(List<String> recipes) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("recipes").forGetter(Source::recipes)
        ).apply(i, Source::new));
    }

    private static volatile Map<Identifier, Source> sources = Map.of();

    private RecipeSources() {}

    public static List<String> recipes(Identifier source) {
        Source s = sources.get(source);
        return s == null ? List.of() : s.recipes();
    }

    public static Map<Identifier, Source> all() {
        return sources;
    }

    /** Replaces every source at once (reload, tests). */
    public static void replaceAll(Map<Identifier, Source> loaded) {
        sources = Map.copyOf(loaded);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<Source> {

        public Loader() {
            super(Source.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, Source> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
            replaceAll(entries);
            LOGGER.info("[Brewing] Loaded {} recipe source(s): {}", entries.size(), entries.keySet());
        }
    }
}
