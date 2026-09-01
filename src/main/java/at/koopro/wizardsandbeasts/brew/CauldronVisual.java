package at.koopro.wizardsandbeasts.brew;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NullMarked;

/**
 * What a cauldron looks like from across the room.
 *
 * <h2>Why this is a blockstate and not just the block entity</h2>
 * <p>Everything a cauldron was doing lived on its block entity, which means it was invisible: the
 * only outward sign was a particle rate, and a player had to walk up and click a pot to find out
 * whether it was empty, full of water, loaded with ingredients, working, finished, or ruined. Six
 * states, one appearance.
 *
 * <p>A blockstate property fixes that for free in several directions at once — models and textures
 * can differ per state, a resource pack can restyle them without touching the mod, and the state is
 * visible to anything that reads blockstates rather than block entities.
 *
 * <h2>Why it is derived, never authored</h2>
 * <p>The block entity is the truth; this is a projection of it, recomputed whenever the pot changes
 * and written into the blockstate. Nothing sets it directly, and nothing reads it back to make a
 * decision — a second, writable copy of the pot's phase is exactly how the two end up disagreeing
 * about whether something is brewing.
 *
 * <p>It is deliberately <em>coarser</em> than {@link CauldronPhase}: IDLE splits into three
 * (nothing in it / water / water and ingredients) because those look different, while BREWING stays
 * one state because the progress is already carried by the particles and does not want six more
 * blockstates.
 */
@NullMarked
public enum CauldronVisual implements StringRepresentable {

    /** Nothing in it. */
    EMPTY,

    /** Filled with water, waiting for ingredients. */
    WATER,

    /** Water and at least one ingredient, not yet started. */
    INGREDIENTS,

    /** On the heat and working. */
    BREWING,

    /** Finished, holding a brew somebody has not bottled yet. */
    DONE,

    /** Ruined — curdled off the heat, or a failed roll at completion. */
    SPOILED;

    public static final EnumProperty<CauldronVisual> PROPERTY =
            EnumProperty.create("visual", CauldronVisual.class);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The visual a pot in this condition should be showing.
     *
     * <p>Pure, so the projection rule is one expression that can be read and tested rather than a
     * scattering of {@code setBlock} calls that each decide part of it.
     */
    public static CauldronVisual of(CauldronPhase phase, boolean filled, boolean hasIngredients) {
        return switch (phase) {
            case BREWING -> BREWING;
            case DONE -> DONE;
            case SPOILED -> SPOILED;
            case IDLE -> !filled ? EMPTY : (hasIngredients ? INGREDIENTS : WATER);
        };
    }

    /** Whether a pot showing this has liquid in it worth tinting. */
    public boolean hasLiquid() {
        return this != EMPTY;
    }
}
