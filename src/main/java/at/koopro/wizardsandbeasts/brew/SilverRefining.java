package at.koopro.wizardsandbeasts.brew;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Dropping an Occamy eggshell into a working cauldron.
 *
 * <p>An Occamy's shell is pure silver, and putting it into a silver-based brew that is already
 * heating refines the whole batch rather than adding to it. That is what the eggshell is <em>for</em>
 * — it is not an ingredient in a recipe, it is a catalyst you throw at a brew already in progress,
 * which is why it works through this class instead of through {@code BrewingRecipes}.
 *
 * <h2>Timing is the mechanic</h2>
 * The shell only works on a batch that is actually {@link CauldronPhase#BREWING}. A cauldron that has
 * finished holds its brew, but the batch has set — refining is something you do to a potion still on
 * the heat, so a finished pot is refused exactly like a cold one. Miss the window and you have a
 * bottle of the unrefined thing and one fewer shell.
 *
 * <h2>What counts as silver-based</h2>
 * Whatever declares itself so. A {@link Brew} names the brew it refines into via
 * {@code silverVariant}; a brew that names nothing is not silver-based. No name matching, no tag, no
 * list in code — a datapack adds a silver brew by writing one field.
 */
@NullMarked
public final class SilverRefining {

    /** What happened when the shell went in. {@code message} is presentable either way. */
    public record Result(boolean refined, Component message) {

        static Result no(String key, Object... args) {
            return new Result(false, Component.translatable(key, args));
        }
    }

    private SilverRefining() {}

    /**
     * Refines the brew heating at {@code pos}, if there is one and it is silver-based.
     *
     * <p>Consumes nothing on failure: an eggshell is rare enough that spending one on a cold cauldron
     * would be a trap rather than a cost.
     */
    public static Result refine(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos)
                instanceof at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity be)) {
            return Result.no("brew.wizards_and_beasts.silver.nothing_brewing");
        }
        String brewId = be.phase() == CauldronPhase.BREWING ? be.brewId() : null;
        if (brewId == null) {
            return Result.no("brew.wizards_and_beasts.silver.nothing_brewing");
        }
        Brew brewing = Brews.byId(brewId);
        if (brewing == null || !brewing.isSilverBased()) {
            return Result.no("brew.wizards_and_beasts.silver.not_silver",
                    brewing == null ? Component.literal(brewId) : Component.translatable(brewing.displayName()));
        }
        Brew refined = Brews.byId(brewing.silverVariant());
        if (refined == null) {
            // The brew declared a variant that does not exist. A datapack error, not a player one —
            // say so plainly rather than silently eating the shell.
            return Result.no("brew.wizards_and_beasts.silver.missing_variant",
                    Component.literal(brewing.silverVariant()));
        }
        if (!be.retarget(refined.id())) {
            return Result.no("brew.wizards_and_beasts.silver.nothing_brewing");
        }

        celebrate(level, pos);
        return new Result(true, Component.translatable("brew.wizards_and_beasts.silver.refined",
                Component.translatable(refined.displayName())));
    }

    /** The moment the batch turns. Loud enough to be worth watching for. */
    private static void celebrate(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9f, 1.4f);
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.5f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 18, 0.25, 0.25, 0.25, 0.02);
        }
    }

    /** The brew a cauldron would refine into, for tooltips and tests; {@code null} if not silver-based. */
    public static @Nullable Brew variantOf(@Nullable Brew brew) {
        if (brew == null || !brew.isSilverBased()) {
            return null;
        }
        return Brews.byId(brew.silverVariant());
    }
}
