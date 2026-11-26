package at.koopro.wizardsandbeasts.floo.hearth;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The loaded hearth styles ({@code data/<ns>/floo_hearth/*.json}) and the world-side check of a build against them.
 * A malformed file is logged with every problem and left out, never half-used.
 */
@NullMarked
public final class HearthPatterns extends SimpleJsonResourceReloadListener<HearthPattern> {

    public static final String DIRECTORY = "floo_hearth";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Identifier, HearthPattern> SERVER = new LinkedHashMap<>();

    /** A build recognised as a style. */
    public record Match(Identifier style, HearthPattern pattern) {}

    public HearthPatterns() {
        super(HearthPattern.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, HearthPattern> loaded, ResourceManager resources, ProfilerFiller profiler) {
        SERVER.clear();
        loaded.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(e -> {
                    List<String> problems = e.getValue().problems();
                    if (problems.isEmpty()) {
                        SERVER.put(e.getKey(), e.getValue());
                    } else {
                        LOGGER.error("Floo hearth {} left out: {}", e.getKey(), problems);
                    }
                });
        LOGGER.info("Loaded {} Floo hearth styles", SERVER.size());
    }

    public static Map<Identifier, HearthPattern> all() {
        return Map.copyOf(SERVER);
    }

    public static Optional<HearthPattern> get(Identifier style) {
        return Optional.ofNullable(SERVER.get(style));
    }

    /** The first style (by id) the build around this grate completes, if any. */
    public static Optional<Match> match(BlockGetter level, BlockPos grate, Direction facing) {
        for (Map.Entry<Identifier, HearthPattern> e : SERVER.entrySet()) {
            if (e.getValue().firstMismatch(grate, facing, check(level, e.getValue())).isEmpty()) {
                return Optional.of(new Match(e.getKey(), e.getValue()));
            }
        }
        return Optional.empty();
    }

    /** The world-side meaning of each symbol. */
    public static HearthPattern.CellCheck check(BlockGetter level) {
        return (symbol, pos) -> {
            BlockState state = level.getBlockState(pos);
            return switch (symbol) {
                case HearthPattern.GRATE -> state.is(ModBlocks.FLOO_GRATE.get());
                case HearthPattern.OPEN -> state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
                case HearthPattern.ANY -> true;
                default -> false;
            };
        };
    }

    /** {@link #check(BlockGetter)} with the pattern's own key for every other symbol. */
    public static HearthPattern.CellCheck check(BlockGetter level, HearthPattern pattern) {
        HearthPattern.CellCheck reserved = check(level);
        return (symbol, pos) -> {
            String entry = pattern.key().get(String.valueOf(symbol));
            if (entry == null) {
                return reserved.matches(symbol, pos);
            }
            return matches(level.getBlockState(pos), entry);
        };
    }

    static boolean matches(BlockState state, String entry) {
        if (entry.startsWith("#")) {
            Identifier tag = Identifier.tryParse(entry.substring(1));
            return tag != null && state.is(TagKey.create(Registries.BLOCK, tag));
        }
        // getOptional, not getValue: an unknown id must not fall back to air and match every open cell.
        Identifier id = Identifier.tryParse(entry);
        Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
        return block.isPresent() && state.is(block.get());
    }
}
