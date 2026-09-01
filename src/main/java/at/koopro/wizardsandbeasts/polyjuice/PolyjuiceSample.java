package at.koopro.wizardsandbeasts.polyjuice;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Whose hair went into a bottle.
 *
 * <h2>Why it lives on the stack</h2>
 * <p>Every bottle of Polyjuice is the same {@link at.koopro.wizardsandbeasts.brew.Brew}. What makes
 * one of them a disguise of a particular person is the sample it was brewed with, and that is a
 * property of the bottle rather than of the recipe — so it is a data component, read at the moment
 * somebody drinks it.
 *
 * <p>Stored as one string, {@code "<uuid> <name>"}, rather than two components. The halves are
 * meaningless apart: a UUID with no name cannot be drawn on a nameplate and a name with no UUID
 * cannot fetch a skin. One field makes a half-set sample unrepresentable rather than merely unlikely.
 *
 * <h2>Until the cauldron takes hair</h2>
 * <p>The station accepts arbitrary ingredients but has no notion of an item that carries an identity,
 * so there is no hair item yet. Samples are set with {@code /wandb brew polyjuice sample} in the
 * meantime — see {@code PolyjuiceCommands}. When a hair item lands, it writes this same component and
 * nothing downstream changes.
 */
@NullMarked
public record PolyjuiceSample(Optional<UUID> id, String name) {

    public static final PolyjuiceSample NONE = new PolyjuiceSample(Optional.empty(), "");

    /**
     * Read the sample off a bottle.
     *
     * <p>A malformed component reads as no sample rather than throwing. The component is persisted in
     * a save and synced over the network, so it can arrive corrupt, hand-edited, or written by an
     * older version — and the correct answer to all of those is "this bottle lacks a sample", which
     * the drink path already knows how to say.
     */
    public static PolyjuiceSample read(ItemStack stack) {
        String raw = stack.get(ModDataComponents.POLYJUICE_TARGET.get());
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        int space = raw.indexOf(' ');
        if (space <= 0 || space == raw.length() - 1) {
            return NONE;
        }
        try {
            UUID id = UUID.fromString(raw.substring(0, space));
            String name = raw.substring(space + 1).strip();
            return name.isEmpty() ? NONE : new PolyjuiceSample(Optional.of(id), name);
        } catch (IllegalArgumentException malformed) {
            return NONE;
        }
    }

    /** Write a sample onto a bottle. */
    public static void write(ItemStack stack, UUID id, String name) {
        stack.set(ModDataComponents.POLYJUICE_TARGET.get(), id + " " + name);
    }

    public boolean isPresent() {
        return id.isPresent() && !name.isBlank();
    }
}
