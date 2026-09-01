package at.koopro.wizardsandbeasts.client.brew;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.CauldronVisual;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What colour the inside of a cauldron is.
 *
 * <h2>Why a tint and not six textures</h2>
 * <p>The opening carries {@code tintindex 0}, so one model shows six conditions — and the brewing
 * colour is the <em>brew's own</em>, read from the block entity, which no amount of authored art
 * could do. A pot of Wiggenweld and a pot of Felix look different because the potions are different
 * colours, not because somebody drew two lids.
 *
 * <p>It also costs no new PNGs, which matters: art is the slowest thing in this repo to produce and
 * the easiest to get wrong, and a mechanic that has to wait for a texture ships late or not at all.
 *
 * <h2>Client only, and forgiving</h2>
 * <p>Every lookup can fail — a block entity that has not synced yet, a brew id from a datapack this
 * client does not have — and every failure falls back to the steeping colour or to no tint at all.
 * A pot that renders slightly wrong for one frame is invisible; one that renders as a white blowout
 * is a bug report.
 */
@NullMarked
public final class CauldronColors {

    // Every value here is deliberately BRIGHT, because a block tint multiplies the texture rather
    // than replacing it. The cauldron's opening averages about RGB(112,120,132) — a mid grey — so a
    // saturated mid-tone tint lands somewhere around a third of that and reads as mud. Pale, strongly
    // hued values are what survive the multiply as a recognisable colour.

    /** Plain water. */
    private static final int WATER = 0xFF7FB2FF;

    /** Water with things steeping in it: green, not yet a potion. */
    private static final int STEEPING = 0xFFA8D07A;

    /** A ruined batch. Washed-out grey-brown. */
    private static final int SPOILED = 0xFFB0A08C;

    /**
     * Identity. Vanilla treats a -1 tint as white, so the face renders exactly as its texture.
     *
     * <p>This is what an EMPTY pot returns, and it used to return the metal's own idle colour
     * instead — which multiplied the texture by itself and made every empty cauldron in the world
     * visibly darker than before the tint existed. An empty pot should look like it always did.
     */
    private static final int NO_TINT = -1;

    /** How far a brew's colour is pulled towards white before being used as a multiply tint. */
    private static final float LIGHTEN = 0.45f;

    private CauldronColors() {}

    public static void register(RegisterColorHandlersEvent.Block event) {
        event.register(CauldronColors::tint,
                ModBlocks.PEWTER_CAULDRON.get(),
                ModBlocks.BRASS_CAULDRON.get(),
                ModBlocks.WIZARDING_COPPER_CAULDRON.get());
    }

    private static int tint(BlockState state, net.minecraft.world.level.BlockAndTintGetter level,
                            net.minecraft.core.BlockPos pos, int tintIndex) {
        if (tintIndex != 0 || !state.hasProperty(CauldronVisual.PROPERTY)) {
            return NO_TINT;
        }
        CauldronVisual visual = state.getValue(CauldronVisual.PROPERTY);
        return switch (visual) {
            case EMPTY -> NO_TINT;
            case WATER -> WATER;
            case INGREDIENTS -> STEEPING;
            case SPOILED -> SPOILED;
            // The two that show the potion itself. Brewing crosses from steeping towards the finished
            // colour, so a pot visibly becomes the thing it is making.
            case BREWING -> brewTint(level, pos, STEEPING, progressAt(level, pos));
            case DONE -> brewTint(level, pos, STEEPING, 1.0f);
        };
    }

    private static float progressAt(net.minecraft.world.level.BlockAndTintGetter level,
                                    net.minecraft.core.BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CauldronBlockEntity be ? be.progress() : 0f;
    }

    /**
     * The brew's colour, blended in by how far along it is.
     *
     * <p>Falls back to {@code from} when the block entity or the brew is not available — which on a
     * client that has just walked into range is a normal, momentary state rather than an error.
     */
    private static int brewTint(net.minecraft.world.level.BlockAndTintGetter level,
                                net.minecraft.core.BlockPos pos, int from, float progress) {
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            return from;
        }
        Brew brew = Brews.byId(be.brewId());
        if (brew == null) {
            return from;
        }
        // Lightened before use. A brew colour is authored to be the colour of the potion in a bottle,
        // which is already dark for most of them; multiplying a dark colour into a mid-grey texture
        // produces something closer to black than to the potion.
        int lightened = lerp(brew.color(), 0xFFFFFFFF, LIGHTEN);
        return lerp(from, lightened, Math.min(1f, Math.max(0f, progress)));
    }

    /** Straight-line blend between two colours, alpha forced opaque. */
    private static int lerp(int from, int to, float t) {
        int r = channel(from, 16) + (int) ((channel(to, 16) - channel(from, 16)) * t);
        int g = channel(from, 8) + (int) ((channel(to, 8) - channel(from, 8)) * t);
        int b = channel(from, 0) + (int) ((channel(to, 0) - channel(from, 0)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int channel(int colour, int shift) {
        return (colour >> shift) & 0xFF;
    }
}
