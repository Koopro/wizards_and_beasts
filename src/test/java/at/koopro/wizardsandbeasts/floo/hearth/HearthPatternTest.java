package at.koopro.wizardsandbeasts.floo.hearth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hearth pattern's geometry (Floo remake P2): every shipped style is well formed, a build stamped in any facing
 * is recognised in that facing only, and the first missing block is the one reported.
 */
class HearthPatternTest {

    private static final Path DIR = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "floo_hearth");
    private static final Path TAGS = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "tags", "block");
    private static final Gson GSON = new Gson();

    private static List<Path> files() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static HearthPattern parse(Path json) throws IOException {
        JsonElement element = GSON.fromJson(Files.readString(json), JsonElement.class);
        return HearthPattern.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
    }

    private static HearthPattern cottage() throws IOException {
        return parse(DIR.resolve("cottage.json"));
    }

    /** The world a player would build: each cell's symbol at its place for a grate facing {@code facing}. */
    private static Map<BlockPos, Character> stamp(HearthPattern pattern, BlockPos grate, Direction facing) {
        Map<BlockPos, Character> world = new HashMap<>();
        for (HearthPattern.Cell cell : pattern.cells()) {
            world.put(HearthPattern.place(grate, facing, cell), cell.symbol());
        }
        return world;
    }

    private static HearthPattern.CellCheck reading(Map<BlockPos, Character> world) {
        return (symbol, pos) -> world.getOrDefault(pos, '?') == symbol;
    }

    @Test
    void everyShippedStyleIsWellFormedAndItsTagsExist() throws IOException {
        List<Path> files = files();
        assertFalse(files.isEmpty(), "no hearth styles shipped");
        for (Path file : files) {
            HearthPattern pattern = parse(file);
            assertEquals(List.of(), pattern.problems(), file.getFileName().toString());
            for (String entry : pattern.key().values()) {
                if (entry.startsWith("#wizards_and_beasts:")) {
                    Path tag = TAGS.resolve(entry.substring("#wizards_and_beasts:".length()) + ".json");
                    assertTrue(Files.exists(tag), file.getFileName() + " names a missing tag " + entry);
                }
            }
        }
    }

    @Test
    void theOpeningLeavesRoomToWalkIn() throws IOException {
        HearthPattern pattern = cottage();
        assertTrue(pattern.openingWidth() >= 2 && pattern.openingHeight() >= 3,
                "body travel needs at least a 2 x 3 opening (CANON.md)");
        // The traveller stands on the grate: the two cells above it must be open.
        List<HearthPattern.Cell> cells = pattern.cells();
        for (int up = 1; up <= 2; up++) {
            int u = up;
            assertTrue(cells.stream().anyMatch(c -> c.right() == 0 && c.front() == 0 && c.up() == u
                    && c.symbol() == HearthPattern.OPEN), "cell " + up + " above the grate must be open");
        }
    }

    @Test
    void aCompleteBuildIsRecognisedInEveryFacing() throws IOException {
        HearthPattern pattern = cottage();
        BlockPos grate = new BlockPos(10, 64, -7);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<BlockPos, Character> world = stamp(pattern, grate, facing);
            assertEquals(Optional.empty(), pattern.firstMismatch(grate, facing, reading(world)), facing.toString());
        }
    }

    @Test
    void everyStyleIsRecognisedInEveryFacingAndWalkInAble() throws IOException {
        for (Path file : files()) {
            HearthPattern pattern = parse(file);
            BlockPos grate = new BlockPos(-3, 70, 12);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Map<BlockPos, Character> world = stamp(pattern, grate, facing);
                assertEquals(Optional.empty(), pattern.firstMismatch(grate, facing, reading(world)),
                        file.getFileName() + " " + facing);
            }
            assertTrue(pattern.openingWidth() >= 2 && pattern.openingHeight() >= 3,
                    file.getFileName() + ": body travel needs at least a 2 x 3 opening (CANON.md)");
            for (String symbol : pattern.example().keySet()) {
                assertTrue(pattern.key().containsKey(symbol), file.getFileName() + ": example for unknown symbol " + symbol);
            }
        }
    }

    @Test
    void aCastleIsTheMoreSpecificReadingOfACastleBuild() throws IOException {
        // A castle build also satisfies the cottage's looser pattern (the cottage leaves the castle's extra cells
        // free); the loader tries the style with more cells first so it is called a castle. Pinned here so a
        // future style cannot silently make that order matter the other way.
        HearthPattern castle = parse(DIR.resolve("castle.json"));
        HearthPattern cottage = parse(DIR.resolve("cottage.json"));
        assertTrue(castle.cells().size() > cottage.cells().size());
    }

    @Test
    void aBuildIsNotRecognisedTurnedTheWrongWay() throws IOException {
        HearthPattern pattern = cottage();
        BlockPos grate = BlockPos.ZERO;
        Map<BlockPos, Character> world = stamp(pattern, grate, Direction.SOUTH);
        for (Direction other : List.of(Direction.NORTH, Direction.EAST, Direction.WEST)) {
            assertTrue(pattern.firstMismatch(grate, other, reading(world)).isPresent(), other.toString());
        }
    }

    @Test
    void theMissingBlockIsTheOneReported() throws IOException {
        HearthPattern pattern = cottage();
        BlockPos grate = BlockPos.ZERO;
        Map<BlockPos, Character> world = stamp(pattern, grate, Direction.EAST);
        HearthPattern.Cell mantel = pattern.cells().stream().filter(c -> c.symbol() == 'M').findFirst().orElseThrow();
        world.remove(HearthPattern.place(grate, Direction.EAST, mantel));
        assertEquals(Optional.of(mantel), pattern.firstMismatch(grate, Direction.EAST, reading(world)));
    }

    @Test
    void rightIsRightAsSeenFromTheRoom() {
        // A hearth facing south (the room lies to the south): the viewer looks north, so their right is east.
        HearthPattern.Cell oneRight = new HearthPattern.Cell(1, 0, 0, 'S');
        assertEquals(new BlockPos(1, 0, 0), HearthPattern.place(BlockPos.ZERO, Direction.SOUTH, oneRight));
        HearthPattern.Cell oneFront = new HearthPattern.Cell(0, 0, 1, 'S');
        assertEquals(new BlockPos(0, 0, 1), HearthPattern.place(BlockPos.ZERO, Direction.SOUTH, oneFront));
    }

    @Test
    void malformedFilesSayWhy() {
        HearthPattern noGrate = new HearthPattern(3, 3, List.of(List.of("SSS")), Map.of("S", "#x:y"));
        assertTrue(noGrate.problems().stream().anyMatch(p -> p.contains("grate")));
        HearthPattern unknown = new HearthPattern(3, 3, List.of(List.of("GX")), Map.of());
        assertTrue(unknown.problems().stream().anyMatch(p -> p.contains("'X'")));
        HearthPattern ragged = new HearthPattern(3, 3, List.of(List.of("G", "SS")), Map.of("S", "#x:y"));
        assertTrue(ragged.problems().stream().anyMatch(p -> p.contains("wide")));
    }
}
