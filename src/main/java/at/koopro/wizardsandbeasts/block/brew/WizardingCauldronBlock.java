package at.koopro.wizardsandbeasts.block.brew;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.CauldronHeat;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.brew.CauldronVisual;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The station. One block class for all three metals; the metal is a constructor argument.
 *
 * <h2>The gestures</h2>
 * <p>Every interaction is a different hand, and none of them overlap:
 * <table border="1">
 *   <tr><th>Phase</th><th>Hand</th><th>Does</th></tr>
 *   <tr><td>IDLE, unfilled</td><td>water bucket</td><td>fill the pot</td></tr>
 *   <tr><td>IDLE</td><td>an ingredient</td><td>add one to the pot</td></tr>
 *   <tr><td>IDLE</td><td>empty hand or bottle</td><td>start brewing</td></tr>
 *   <tr><td>IDLE</td><td>sneak + empty hand</td><td>take the last ingredient back</td></tr>
 *   <tr><td>BREWING</td><td>an ingredient</td><td>timed add — catalyst, or contamination</td></tr>
 *   <tr><td>BREWING</td><td>empty hand or bottle</td><td>refused — it is mid-brew</td></tr>
 *   <tr><td>DONE</td><td>glass bottle</td><td>bottle it</td></tr>
 *   <tr><td>SPOILED</td><td>empty hand or bottle</td><td>pour it away</td></tr>
 * </table>
 *
 * <p>The brief had start on {@code sneak + empty hand} and scoop-out on the same gesture. They cannot
 * both have it, so start moved to a plain empty hand — starting is the common action and deserves the
 * simpler input, and sneaking to take something back out matches every other container in the game.
 *
 * <h2>Why a bottle is not needed to start</h2>
 * <p>The pot holds its finished brew until somebody bottles it. Consuming the bottle up front would
 * mean a brew you cannot collect if you lose the bottle, and it would make "somebody else collects
 * it" impossible — which is one of the things having contents on the block entity is <em>for</em>.
 */
public class WizardingCauldronBlock extends BaseEntityBlock {

    /**
     * Minimum ticks between two interactions with one cauldron by anybody.
     *
     * <p>Four ticks. A held right-click repeats far faster than that, and without a floor it feeds a
     * whole stack into the pot before the player can let go.
     */
    private static final int INTERACT_GAP_TICKS = 4;

    private final CauldronTier tier;
    private final int idleTint;

    public WizardingCauldronBlock(BlockBehaviour.Properties properties, CauldronTier tier, int idleTint) {
        super(properties);
        this.tier = tier;
        this.idleTint = idleTint;
        registerDefaultState(getStateDefinition().any().setValue(CauldronVisual.PROPERTY, CauldronVisual.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<
                    net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(CauldronVisual.PROPERTY);
    }

    /** The idle metal tint, for the colour handler to blend from. */
    public int idleTint() {
        return idleTint;
    }

    public CauldronTier tier() {
        return tier;
    }

    /**
     * Not {@code simpleCodec}: this block takes a tier and a tint beyond its properties, and the
     * simple form can only rebuild a block whose constructor is properties-only.
     */
    public static final MapCodec<WizardingCauldronBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    CauldronTier.CODEC.fieldOf("tier").forGetter(WizardingCauldronBlock::tier),
                    com.mojang.serialization.Codec.INT.fieldOf("idle_tint")
                            .forGetter(b -> b.idleTint)
            ).apply(instance, WizardingCauldronBlock::new));

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new CauldronBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.CAULDRON.get(), CauldronBlockEntity::serverTick);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    // -- interaction -----------------------------------------------------------------------------

    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state,
                                                   @NonNull Level level, @NonNull BlockPos pos,
                                                   @NonNull Player player, @NonNull InteractionHand hand,
                                                   @NonNull BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)
                || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        // The empty-hand fall-through happens BEFORE the anti-spam guard, and that ordering is the
        // whole of a bug that made start, scoop and pour-away silently do nothing.
        //
        // Vanilla runs useItemOn first and, on TRY_WITH_EMPTY_HAND, calls useWithoutItem on the same
        // tick. Both methods consulted the guard, so the first call stamped lastInteractGameTime and
        // the second — zero ticks later — was refused as a repeat of itself. Every empty-hand gesture
        // on this block was eaten by its own rate limiter.
        //
        // Nothing above this line has consumed the guard, so useWithoutItem gets the one and only
        // check, which is what it was always supposed to have.
        if (stack.isEmpty()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!be.acceptInteraction(level.getGameTime(), INTERACT_GAP_TICKS)) {
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.WATER_BUCKET)) {
            return fill(level, pos, be, sp, stack);
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            return bottleOrStart(level, pos, be, sp, stack);
        }
        return addIngredient(level, pos, be, sp, stack);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                        @NonNull BlockPos pos, @NonNull Player player,
                                                        @NonNull BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)
                || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!be.acceptInteraction(level.getGameTime(), INTERACT_GAP_TICKS)) {
            return InteractionResult.SUCCESS;
        }

        return switch (be.phase()) {
            case BREWING -> {
                say(sp, "brew.wizards_and_beasts.cauldron.mid_brew");
                yield InteractionResult.SUCCESS;
            }
            case DONE -> {
                say(sp, "brew.wizards_and_beasts.cauldron.needs_bottle");
                yield InteractionResult.SUCCESS;
            }
            case SPOILED -> pourAway(level, pos, be, sp, ItemStack.EMPTY);
            case IDLE -> sp.isShiftKeyDown()
                    ? scoop(level, pos, be, sp)
                    : start(level, pos, be, sp);
        };
    }

    private InteractionResult fill(Level level, BlockPos pos, CauldronBlockEntity be,
                                    ServerPlayer player, ItemStack bucket) {
        if (be.phase() != CauldronPhase.IDLE) {
            say(player, "brew.wizards_and_beasts.cauldron.mid_brew");
            return InteractionResult.SUCCESS;
        }
        if (be.isFilled()) {
            say(player, "brew.wizards_and_beasts.cauldron.already_filled");
            return InteractionResult.SUCCESS;
        }
        be.setFilled(true);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(player.getUsedItemHand(), new ItemStack(Items.BUCKET));
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        say(player, "brew.wizards_and_beasts.cauldron.filled");
        return InteractionResult.SUCCESS;
    }

    private InteractionResult addIngredient(Level level, BlockPos pos, CauldronBlockEntity be,
                                             ServerPlayer player, ItemStack stack) {
        if (be.phase() == CauldronPhase.BREWING) {
            return timedAdd(level, pos, be, player, stack);
        }
        if (be.phase() != CauldronPhase.IDLE) {
            say(player, "brew.wizards_and_beasts.cauldron.mid_brew");
            return InteractionResult.SUCCESS;
        }
        if (!be.isFilled()) {
            say(player, "brew.wizards_and_beasts.cauldron.needs_water");
            return InteractionResult.SUCCESS;
        }
        if (!be.addIngredient(stack)) {
            say(player, "brew.wizards_and_beasts.cauldron.full");
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.4f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH,
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.2, 0.05, 0.2, 0.0);
        }
        PlayerFeedback.actionBar(player, Component.translatable(
                "brew.wizards_and_beasts.cauldron.added", stack.getHoverName(), be.usedSlots(),
                CauldronBlockEntity.SLOTS));
        return InteractionResult.SUCCESS;
    }

    /**
     * Something dropped into a pot that is already working.
     *
     * <p>The item is always consumed, whatever it was. That is the point: a mistimed catalyst and a
     * handful of gravel both cost you the item and move the odds, and a version that gave the item
     * back on a miss would make fishing for the window free.
     */
    private InteractionResult timedAdd(Level level, BlockPos pos, CauldronBlockEntity be,
                                        ServerPlayer player, ItemStack stack) {
        CauldronBlockEntity.TimedAddResult result = be.timedAdd(stack);
        if (result == CauldronBlockEntity.TimedAddResult.CATALYST_ALREADY) {
            say(player, "brew.wizards_and_beasts.cauldron.catalyst_already");
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        switch (result) {
            case CATALYST_ACCEPTED -> {
                say(player, "brew.wizards_and_beasts.cauldron.catalyst_accepted");
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9f, 1.4f);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.END_ROD,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.25, 0.2, 0.25, 0.02);
                }
            }
            case CATALYST_MISTIMED -> {
                say(player, "brew.wizards_and_beasts.cauldron.catalyst_mistimed");
                spoilPuff(level, pos);
            }
            case CONTAMINATED -> {
                say(player, "brew.wizards_and_beasts.cauldron.contaminated");
                spoilPuff(level, pos);
            }
            default -> { }
        }
        return InteractionResult.SUCCESS;
    }

    /** The pot objecting. Used for both a mistimed catalyst and outright contamination. */
    private static void spoilPuff(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.7f, 0.6f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 14, 0.25, 0.2, 0.25, 0.02);
        }
    }

    private InteractionResult start(Level level, BlockPos pos, CauldronBlockEntity be, ServerPlayer player) {
        if (!CauldronHeat.hasHeatSource(level, pos.below())) {
            say(player, "brew.wizards_and_beasts.cauldron.cold");
            return InteractionResult.SUCCESS;
        }
        CauldronBlockEntity.StartResult result = be.startBrewing(tier, player);
        switch (result) {
            case STARTED -> {
                Brew brew = Brews.byId(be.brewId());
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.0f);
                PlayerFeedback.actionBar(player, Component.translatable(
                        "brew.wizards_and_beasts.started",
                        brew == null ? Component.literal("?") : Component.translatable(brew.displayName()),
                        be.totalTicks()));
            }
            case NOT_FILLED -> say(player, "brew.wizards_and_beasts.cauldron.needs_water");
            case EMPTY -> say(player, "brew.wizards_and_beasts.cauldron.empty");
            case NO_MATCH -> say(player, "brew.wizards_and_beasts.cauldron.no_match");
            case UNKNOWN_BREW -> say(player, "brew.wizards_and_beasts.cauldron.unknown_brew");
            case NOT_IDLE -> say(player, "brew.wizards_and_beasts.cauldron.mid_brew");
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult scoop(Level level, BlockPos pos, CauldronBlockEntity be, ServerPlayer player) {
        ItemStack taken = be.removeLastIngredient();
        if (taken.isEmpty()) {
            say(player, "brew.wizards_and_beasts.cauldron.empty");
            return InteractionResult.SUCCESS;
        }
        if (!player.getInventory().add(taken)) {
            Block.popResource(level, pos.above(), taken);
        }
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    /**
     * A glass bottle does the obvious thing for the phase it finds.
     *
     * <p>Bottling a finished brew, emptying a ruined one, and starting an idle one are three
     * different actions that all reasonably follow from "I am holding a bottle at a cauldron", so the
     * phase picks. Only the first consumes the bottle.
     */
    private InteractionResult bottleOrStart(Level level, BlockPos pos, CauldronBlockEntity be,
                                             ServerPlayer player, ItemStack bottle) {
        switch (be.phase()) {
            case DONE -> {
                Brew brew = be.collect();
                if (brew == null) {
                    say(player, "brew.wizards_and_beasts.cauldron.unknown_brew");
                    return InteractionResult.SUCCESS;
                }
                if (!player.getAbilities().instabuild) {
                    bottle.shrink(1);
                }
                ItemStack filled = BrewItem.of(brew);
                if (!player.getInventory().add(filled)) {
                    Block.popResource(level, pos.above(), filled);
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                        Component.translatable("brew.wizards_and_beasts.cauldron.bottled"),
                        Component.translatable(brew.displayName()));
                return InteractionResult.SUCCESS;
            }
            case SPOILED -> {
                return pourAway(level, pos, be, player, bottle);
            }
            case BREWING -> {
                say(player, "brew.wizards_and_beasts.cauldron.mid_brew");
                return InteractionResult.SUCCESS;
            }
            case IDLE -> {
                return start(level, pos, be, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Empty a ruined batch.
     *
     * <p>With a bottle in hand you get the sludge; with an empty hand you tip it out. Bottling a
     * failure rather than getting nothing is the difference between a setback and a punishment — and
     * Ruined Potion is a real item with real (bad) effects, so it is worth having.
     */
    private InteractionResult pourAway(Level level, BlockPos pos, CauldronBlockEntity be,
                                        ServerPlayer player, ItemStack bottle) {
        Brew ruined = Brews.byId(RUINED_BREW_ID);
        if (!bottle.isEmpty() && ruined != null) {
            if (!player.getAbilities().instabuild) {
                bottle.shrink(1);
            }
            ItemStack filled = BrewItem.of(ruined);
            if (!player.getInventory().add(filled)) {
                Block.popResource(level, pos.above(), filled);
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 0.7f);
            say(player, "brew.wizards_and_beasts.cauldron.bottled_ruined");
        } else {
            say(player, "brew.wizards_and_beasts.cauldron.poured_away");
        }
        be.discardSpoiled();
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 0.8f);
        return InteractionResult.SUCCESS;
    }

    /** The consolation prize. Defined in data; absent means a ruined pot simply gives nothing. */
    private static final String RUINED_BREW_ID = "wizards_and_beasts:ruined_potion";

    private static void say(ServerPlayer player, String key) {
        PlayerFeedback.actionBar(player, Component.translatable(key));
    }

    // -- lifecycle -------------------------------------------------------------------------------

    /**
     * Breaking the pot spills the ingredients and voids anything mid-brew.
     *
     * <p>Ingredients drop because they are still items and the player put them there. A partial brew
     * does not, because there is no item for "half a Wiggenweld" — and a finished one does not either,
     * on the grounds that smashing the cauldron is not a bottling technique.
     */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        if (!level.getBlockState(pos).is(this)
                && level.getBlockEntity(pos) instanceof CauldronBlockEntity be) {
            for (ItemStack stack : be.contentsForDrop()) {
                if (!stack.isEmpty()) {
                    Block.popResource(level, pos, stack);
                }
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    // -- looks -----------------------------------------------------------------------------------

    /**
     * Bubbles that say what the pot is doing without opening anything.
     *
     * <p>Rate rises with progress and the colour crosses from the cauldron's own tint to the brew's,
     * so a passer-by can tell an idle pot from one about to finish, and two different brews apart,
     * from across a room. That is the acceptance criterion about a spectator being able to see
     * somebody else's cauldron working.
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
                            @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            return;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.95;
        double cz = pos.getZ() + 0.5;

        switch (be.phase()) {
            // The tinted face is the pot's OPENING, which a player standing beside a cauldron cannot
            // see — they are looking at the metal sides. So the idle states need a signal that rises
            // above the rim, or a filled pot and an empty one are identical from every angle a player
            // actually stands at. These particles are that signal, and they are deliberately frequent
            // enough to read at a glance rather than being an occasional flourish.
            case IDLE -> {
                if (!be.isFilled()) {
                    break;
                }
                boolean loaded = !be.isEmptyOfIngredients();
                if (random.nextInt(loaded ? 4 : 6) == 0) {
                    level.addParticle(ParticleTypes.SPLASH,
                            cx + (random.nextDouble() - 0.5) * 0.5, cy,
                            cz + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
                }
                // Ingredients steeping get a slow green drift above the rim; plain water does not.
                // That is the difference a player most needs to see, because it is the one that says
                // "this pot is ready to start".
                if (loaded && random.nextInt(6) == 0) {
                    level.addParticle(new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(
                                    at.koopro.wizardsandbeasts.registry.ModParticles.ARCANE_MOTE.get(),
                                    0xFF8FBF5A),
                            cx + (random.nextDouble() - 0.5) * 0.45, cy + random.nextDouble() * 0.25,
                            cz + (random.nextDouble() - 0.5) * 0.45, 0, 0.015, 0);
                }
            }
            case BREWING -> {
                float progress = be.progress();
                int count = 1 + (int) (progress * 5);
                Brew brew = Brews.byId(be.brewId());
                int tint = brew == null ? idleTint : lerpColour(idleTint, brew.color(), progress);
                for (int i = 0; i < count; i++) {
                    level.addParticle(new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(
                                    at.koopro.wizardsandbeasts.registry.ModParticles.ARCANE_MOTE.get(), tint),
                            cx + (random.nextDouble() - 0.5) * 0.55, cy + random.nextDouble() * 0.2,
                            cz + (random.nextDouble() - 0.5) * 0.55, 0, 0.02, 0);
                }
                if (random.nextInt(8) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, cx, cy, cz, 0, 0.02, 0);
                }
            }
            case DONE -> {
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.END_ROD,
                            cx + (random.nextDouble() - 0.5) * 0.4, cy + 0.1,
                            cz + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
                }
            }
            case SPOILED -> {
                if (random.nextInt(6) == 0) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE,
                            cx + (random.nextDouble() - 0.5) * 0.4, cy,
                            cz + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
                }
            }
        }
    }

    /** Straight-line blend between two opaque ARGB colours. */
    private static int lerpColour(int from, int to, float t) {
        float clamped = Math.min(1f, Math.max(0f, t));
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
