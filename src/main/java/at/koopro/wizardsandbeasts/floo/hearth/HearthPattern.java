package at.koopro.wizardsandbeasts.floo.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How a Floo hearth of one style is built, block by block (Floo remake P2, 2026-11-26). One file per style in
 * {@code data/<ns>/floo_hearth/}; the same file is what the grate checks a build against and what the handbook's build
 * guide draws (P6), so the two can never disagree.
 *
 * <h2>Layout</h2>
 * {@code layers} run bottom to top; each layer is a list of rows from the back of the hearth to the front (the room
 * side); each row is read left to right as seen from the room. Every layer has the same rows, every row the same
 * length. Reserved symbols:
 * <ul>
 *   <li>{@code G} the Floo grate, exactly once: the origin of the build;</li>
 *   <li>{@code .} must be open (nothing a body bumps into): the opening a traveller walks into;</li>
 *   <li>{@code ' '} (space) anything.</li>
 * </ul>
 * Every other symbol names an entry in {@code key}: {@code "#namespace:tag"} or a block id.
 */
@NullMarked
public record HearthPattern(int openingWidth, int openingHeight, List<List<String>> layers, Map<String, String> key) {

    public static final char GRATE = 'G';
    public static final char OPEN = '.';
    public static final char ANY = ' ';

    public static final Codec<HearthPattern> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("opening_width").forGetter(HearthPattern::openingWidth),
            Codec.INT.fieldOf("opening_height").forGetter(HearthPattern::openingHeight),
            Codec.STRING.listOf().listOf().fieldOf("layers").forGetter(HearthPattern::layers),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("key").forGetter(HearthPattern::key)
    ).apply(i, HearthPattern::new));

    /** One required cell, relative to the grate: {@code right} as seen from the room, {@code up}, {@code front}. */
    public record Cell(int right, int up, int front, char symbol) {}

    /** Answers whether the world at {@code pos} satisfies {@code symbol}. */
    @FunctionalInterface
    public interface CellCheck {
        boolean matches(char symbol, BlockPos pos);
    }

    /** What is wrong with this file, empty when it is well formed. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (layers.isEmpty()) {
            out.add("no layers");
            return out;
        }
        int rows = layers.getFirst().size();
        int cols = rows == 0 ? 0 : layers.getFirst().getFirst().length();
        int grates = 0;
        for (int y = 0; y < layers.size(); y++) {
            List<String> layer = layers.get(y);
            if (layer.size() != rows) {
                out.add("layer " + y + " has " + layer.size() + " rows, expected " + rows);
            }
            for (int r = 0; r < layer.size(); r++) {
                String row = layer.get(r);
                if (row.length() != cols) {
                    out.add("layer " + y + " row " + r + " is " + row.length() + " wide, expected " + cols);
                }
                for (char c : row.toCharArray()) {
                    if (c == GRATE) {
                        grates++;
                    } else if (c != OPEN && c != ANY && !key.containsKey(String.valueOf(c))) {
                        out.add("symbol '" + c + "' (layer " + y + ") is not in key");
                    }
                }
            }
        }
        if (grates != 1) {
            out.add("expected exactly one grate 'G', found " + grates);
        }
        for (String k : key.keySet()) {
            if (k.length() != 1 || k.charAt(0) == GRATE || k.charAt(0) == OPEN || k.charAt(0) == ANY) {
                out.add("key '" + k + "' must be one character and not G, '.' or space");
            }
        }
        if (openingWidth < 1 || openingHeight < 1) {
            out.add("opening must be at least 1 x 1");
        }
        return out;
    }

    /** Every cell that is not "anything", relative to the grate. Assumes {@link #problems()} is empty. */
    public List<Cell> cells() {
        int gy = -1, gr = -1, gc = -1;
        for (int y = 0; y < layers.size() && gy < 0; y++) {
            for (int r = 0; r < layers.get(y).size() && gy < 0; r++) {
                int c = layers.get(y).get(r).indexOf(GRATE);
                if (c >= 0) {
                    gy = y;
                    gr = r;
                    gc = c;
                }
            }
        }
        List<Cell> out = new ArrayList<>();
        for (int y = 0; y < layers.size(); y++) {
            for (int r = 0; r < layers.get(y).size(); r++) {
                String row = layers.get(y).get(r);
                for (int c = 0; c < row.length(); c++) {
                    char symbol = row.charAt(c);
                    if (symbol != ANY) {
                        out.add(new Cell(c - gc, y - gy, r - gr, symbol));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Where a cell lies in the world for a grate at {@code grate} whose front faces {@code facing} (toward the room).
     * "Right as seen from the room" is the counter-clockwise turn of the facing: a viewer in the room looks against it.
     */
    public static BlockPos place(BlockPos grate, Direction facing, Cell cell) {
        Direction right = facing.getCounterClockWise();
        return grate.offset(
                right.getStepX() * cell.right() + facing.getStepX() * cell.front(),
                cell.up(),
                right.getStepZ() * cell.right() + facing.getStepZ() * cell.front());
    }

    /** The first cell the world does not satisfy, or empty when the build is complete. */
    public Optional<Cell> firstMismatch(BlockPos grate, Direction facing, CellCheck check) {
        for (Cell cell : cells()) {
            if (!check.matches(cell.symbol(), place(grate, facing, cell))) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
