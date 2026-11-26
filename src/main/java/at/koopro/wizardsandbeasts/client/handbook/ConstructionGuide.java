package at.koopro.wizardsandbeasts.client.handbook;

import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPattern;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPatterns;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The state of one build-guide page (Floo remake P6, 2026-11-26), in the spirit of Create's Ponder scenes: the build is
 * shown layer by layer from the bottom, each new layer dropping onto the last; it turns slowly on its own and can be
 * dragged round; the left page lists what the current layer takes. Everything comes from the hearth pattern the server
 * synced, the same file the grate checks a build against.
 *
 * <p>A tag symbol ("any hearth stone") is shown as one block of the tag at a time, cycling through the tag every
 * second and a half, so the page says "any of these" without a word.
 */
@NullMarked
final class ConstructionGuide {

    /** One material line of the current layer. */
    record Material(ItemStack icon, Component name, int count) {}

    private static final long DROP_MS = 450L;
    private static final long CYCLE_MS = 1500L;
    private static final float TURN_DEG_PER_MS = 0.012f;

    private final Identifier structure;
    private final List<Integer> layers = new ArrayList<>();
    private int step;
    private long stepAt = Util.getMillis();
    private float yaw = -30.0f;
    private long turnedAt = Util.getMillis();
    private boolean dragging;

    ConstructionGuide(Identifier structure) {
        this.structure = structure;
        pattern().ifPresent(p -> {
            TreeSet<Integer> ups = new TreeSet<>();
            p.cells().forEach(c -> ups.add(c.up()));
            layers.addAll(ups);
        });
    }

    Identifier structure() {
        return structure;
    }

    Optional<HearthPattern> pattern() {
        return Optional.ofNullable(HearthPatterns.client().get(structure));
    }

    int steps() {
        return layers.size();
    }

    int step() {
        return step;
    }

    boolean hasPrev() {
        return step > 0;
    }

    boolean hasNext() {
        return step < layers.size() - 1;
    }

    void move(int delta) {
        int next = Math.max(0, Math.min(layers.size() - 1, step + delta));
        if (next != step) {
            step = next;
            stepAt = Util.getMillis();
        }
    }

    void drag(double dx) {
        dragging = true;
        yaw += (float) dx;
    }

    void release() {
        if (dragging) {
            dragging = false;
            turnedAt = Util.getMillis();
        }
    }

    /** The turntable: still while dragged, turning slowly on its own otherwise. */
    float yaw() {
        long now = Util.getMillis();
        if (!dragging) {
            yaw += (now - turnedAt) * TURN_DEG_PER_MS;
        }
        turnedAt = now;
        return yaw;
    }

    /** How far the current layer still hangs above its place: eased down over {@link #DROP_MS}. */
    float drop() {
        float t = Math.min(1.0f, (Util.getMillis() - stepAt) / (float) DROP_MS);
        return 1.6f * (1.0f - t) * (1.0f - t);
    }

    /** Every block placed up to and including the current layer, centred on the build. */
    List<StructurePreviewRenderState.Placed> blocks() {
        Optional<HearthPattern> pattern = pattern();
        if (pattern.isEmpty() || layers.isEmpty()) {
            return List.of();
        }
        int upTo = layers.get(step);
        List<HearthPattern.Cell> cells = pattern.get().cells();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (HearthPattern.Cell c : cells) {
            BlockPos p = HearthPattern.place(BlockPos.ZERO, Direction.SOUTH, c);
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int cx = (minX + maxX + 1) / 2, cy = (minY + maxY + 1) / 2, cz = (minZ + maxZ + 1) / 2;
        List<StructurePreviewRenderState.Placed> out = new ArrayList<>();
        for (HearthPattern.Cell c : cells) {
            if (c.up() > upTo) {
                continue;
            }
            BlockState state = stateFor(pattern.get(), c.symbol());
            if (state == null) {
                continue;
            }
            BlockPos p = HearthPattern.place(BlockPos.ZERO, Direction.SOUTH, c).offset(-cx, -cy, -cz);
            out.add(new StructurePreviewRenderState.Placed(p, state, c.up() == upTo));
        }
        return out;
    }

    /** The tallest side of the build, in blocks: what the preview is sized to. */
    int extent() {
        return pattern().map(p -> {
            int w = p.layers().getFirst().getFirst().length();
            return Math.max(w, p.layers().size()) + 1;
        }).orElse(6);
    }

    /** What the current layer takes, in the order it is first met; open cells counted as one line. */
    List<Material> materials() {
        Optional<HearthPattern> pattern = pattern();
        if (pattern.isEmpty() || layers.isEmpty()) {
            return List.of();
        }
        int up = layers.get(step);
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (HearthPattern.Cell c : pattern.get().cells()) {
            if (c.up() == up) {
                counts.merge(c.symbol(), 1, Integer::sum);
            }
        }
        List<Material> out = new ArrayList<>();
        counts.forEach((symbol, count) -> {
            BlockState state = stateFor(pattern.get(), symbol);
            ItemStack icon = state == null ? ItemStack.EMPTY : new ItemStack(state.getBlock().asItem());
            out.add(new Material(icon, name(pattern.get(), symbol), count));
        });
        return out;
    }

    private static Component name(HearthPattern pattern, char symbol) {
        if (symbol == HearthPattern.OPEN) {
            return Component.translatable("gui.wizards_and_beasts.handbook.build.open");
        }
        if (symbol == HearthPattern.GRATE) {
            return Component.translatable("floo.wizards_and_beasts.hearth.part.grate");
        }
        String entry = pattern.key().getOrDefault(String.valueOf(symbol), "?");
        String name = entry.substring(entry.indexOf(':') + 1).replace('/', '.');
        return Component.translatableWithFallback("gui.wizards_and_beasts.handbook.build.part." + name,
                Component.translatableWithFallback("floo.wizards_and_beasts.hearth.part." + name, entry).getString());
    }

    /** The block shown for a symbol right now; null for open space. */
    private static @Nullable BlockState stateFor(HearthPattern pattern, char symbol) {
        if (symbol == HearthPattern.OPEN || symbol == HearthPattern.ANY) {
            return null;
        }
        if (symbol == HearthPattern.GRATE) {
            return ModBlocks.FLOO_GRATE.get().defaultBlockState()
                    .setValue(FlooGrateBlock.FACING, Direction.SOUTH).setValue(FlooGrateBlock.FUEL, true);
        }
        String entry = pattern.key().getOrDefault(String.valueOf(symbol), "minecraft:stone");
        BlockState state = Blocks.STONE.defaultBlockState();
        if (entry.startsWith("#")) {
            Identifier tag = Identifier.tryParse(entry.substring(1));
            List<Block> members = new ArrayList<>();
            if (tag != null) {
                for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, tag))) {
                    members.add(holder.value());
                }
            }
            if (!members.isEmpty()) {
                state = members.get((int) ((Util.getMillis() / CYCLE_MS) % members.size())).defaultBlockState();
            }
        } else {
            Identifier id = Identifier.tryParse(entry);
            if (id != null) {
                state = BuiltInRegistries.BLOCK.getOptional(id).map(Block::defaultBlockState).orElse(state);
            }
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            state = state.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        }
        return state;
    }
}
