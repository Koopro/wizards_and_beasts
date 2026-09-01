package at.koopro.wizardsandbeasts.block.brew;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewFailure;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.CauldronHeat;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.brew.CauldronVisual;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectContext;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectPhase;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A cauldron that is actually a station.
 *
 * <h2>What this replaced</h2>
 * <p>Brewing used to live in a {@code static HashMap<BrewSite, ActiveBrew>} in {@code CauldronBrewing},
 * and the ingredients lived in the brewer's backpack. That had four consequences, all of which this
 * class exists to end:
 * <ul>
 *   <li><b>Brews evaporated on restart.</b> The map was never saved. Ingredients had already been
 *       eaten, so a server restart mid-brew silently destroyed them and produced nothing.</li>
 *   <li><b>A cold cauldron held its brew forever.</b> The tick skipped unheated sites rather than
 *       failing them, so the entry sat in the map for the life of the process.</li>
 *   <li><b>The cap was global.</b> 512 abandoned brews anywhere on the server blocked brewing for
 *       everybody. Per-cauldron state has no shared budget to exhaust.</li>
 *   <li><b>Nothing was visible.</b> Ingredients were matched against the player's inventory, so a
 *       cauldron was a trigger you clicked rather than a pot with things in it — nobody could see
 *       what was brewing, and nobody else could contribute.</li>
 * </ul>
 *
 * <h2>Where the ingredients live</h2>
 * <p>{@link #SLOTS} slots on the block entity, filled one interaction at a time. The recipe search
 * runs against <b>these slots only</b> — never the player's inventory — which is the whole point:
 * standing next to a cauldron holding a backpack full of dittany must not start anything.
 *
 * <h2>Why the brew is held rather than popped</h2>
 * <p>The old flow popped a bottle onto the floor the instant the timer ran out. This holds the
 * finished brew in {@link CauldronPhase#DONE} until somebody comes with an empty bottle. It is the
 * difference between a machine that outputs and a pot that has something in it, and it is what makes
 * "collect it later" and "somebody else collects it" both work.
 */
public class CauldronBlockEntity extends BlockEntity {

    /** Ingredient slots. Six is enough for every shipped recipe with room to grow. */
    public static final int SLOTS = 6;

    /**
     * How long a brew survives off the heat before it is ruined. Ten seconds.
     *
     * <p>Long enough to survive a torch being knocked out or a lava source being tidied up, short
     * enough that walking away from a cauldron has a cost. The old behaviour was to pause forever,
     * which meant heat mattered only at the moment you pressed start.
     */
    public static final int SPOIL_TICKS_WITHOUT_HEAT = 200;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private CauldronPhase phase = CauldronPhase.IDLE;
    private boolean filled;
    private @Nullable String brewId;
    private int remainingTicks;
    private int totalTicks;
    private @Nullable UUID brewerId;
    private int brewPoints;
    private int spoilTicksWithoutHeat;
    private int stirCount;

    /** Accumulated in the pot: a missed catalyst, anything wrong dropped in. Never the recipe's own. */
    private float penalties;

    /** Whether this brew's timed add has been satisfied. Meaningless for a recipe without one. */
    private boolean catalystAdded;

    /** The recipe being run, so completion can read its difficulty and its catalyst. */
    private @Nullable String recipeId;
    private long lastInteractGameTime = Long.MIN_VALUE;

    public CauldronBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        super(ModBlockEntities.CAULDRON.get(), pos, state);
    }

    // -- state readers ---------------------------------------------------------------------------

    public CauldronPhase phase() {
        return phase;
    }

    /** Whether the pot has its water base. Nothing brews in an empty cauldron. */
    public boolean isFilled() {
        return filled;
    }

    public @Nullable String brewId() {
        return brewId;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int stirCount() {
        return stirCount;
    }

    /** 0 at the start of a brew, 1 at the end. 0 when nothing is brewing. */
    public float progress() {
        if (totalTicks <= 0) {
            return 0f;
        }
        return Math.min(1f, Math.max(0f, 1f - (float) remainingTicks / totalTicks));
    }

    public boolean isEmptyOfIngredients() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /**
     * The slots as a {@link Container}, for recipe matching.
     *
     * <p>A copy, not a view. Matching must not be able to mutate the pot — the consume step is a
     * separate, deliberate call — and a {@link SimpleContainer} wrapping the live list would hand
     * every caller a way to empty a cauldron by accident.
     */
    public Container contentsView() {
        SimpleContainer container = new SimpleContainer(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            container.setItem(i, items.get(i).copy());
        }
        return container;
    }

    // -- ingredients -----------------------------------------------------------------------------

    /**
     * Put one item in the pot.
     *
     * <p>One at a time on purpose. Dropping a whole stack in would make the pot a hopper and would
     * make the "ordered addition" this class exists to enable impossible to express later — the order
     * things went in is only meaningful if they went in one at a time.
     *
     * @return true when the item was taken
     */
    public boolean addIngredient(ItemStack from) {
        if (!phase.acceptsIngredients() || from.isEmpty()) {
            return false;
        }
        // Merge into a matching stack first, so six slots hold six *kinds* rather than six items.
        for (int i = 0; i < SLOTS; i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, from)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                setChangedAndSync();
                return true;
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                ItemStack one = from.copy();
                one.setCount(1);
                items.set(i, one);
                setChangedAndSync();
                return true;
            }
        }
        return false;
    }

    /** Take the last thing that went in. Used by the sneak-with-empty-hand scoop. */
    public ItemStack removeLastIngredient() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty()) {
                ItemStack one = slot.split(1);
                setChangedAndSync();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Everything in the pot, for dropping when the block breaks. */
    public NonNullList<ItemStack> contentsForDrop() {
        NonNullList<ItemStack> copy = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) {
            copy.set(i, items.get(i).copy());
        }
        return copy;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
        setChangedAndSync();
    }

    // -- the brew --------------------------------------------------------------------------------

    /** Why a start attempt was refused, or that it took. */
    public enum StartResult {
        STARTED,
        NOT_IDLE,
        NOT_FILLED,
        EMPTY,
        NO_MATCH,
        UNKNOWN_BREW
    }

    /**
     * Try to start brewing whatever is in the pot.
     *
     * <p>The recipe search reads {@link #contentsView()} and nothing else. That is the acceptance
     * criterion this whole slice was written for: a player carrying a second set of ingredients must
     * not be able to start a brew the cauldron cannot actually make.
     */
    public StartResult startBrewing(CauldronTier tier, ServerPlayer brewer) {
        return startBrewing(tier, brewer.getUUID());
    }

    /**
     * Start brewing, crediting {@code brewerId}.
     *
     * <p>Takes the id rather than the player because that is all the pot ever needed: the OWL credit
     * is looked up by UUID at completion, and the player object captured at start might have logged
     * out, changed dimension, or died long before the bottle exists. Holding a reference to them for
     * the length of a brew was never right.
     */
    public StartResult startBrewing(CauldronTier tier, java.util.UUID brewerId) {
        if (phase != CauldronPhase.IDLE) {
            return StartResult.NOT_IDLE;
        }
        if (!filled) {
            return StartResult.NOT_FILLED;
        }
        if (isEmptyOfIngredients()) {
            return StartResult.EMPTY;
        }
        BrewingRecipe recipe = BrewingRecipes.findMatch(contentsView(), tier);
        if (recipe == null) {
            return StartResult.NO_MATCH;
        }
        Brew brew = Brews.byId(recipe.outputBrewId());
        if (brew == null) {
            return StartResult.UNKNOWN_BREW;
        }

        consumeFromSlots(recipe);
        this.recipeId = recipe.id();
        this.penalties = 0f;
        this.catalystAdded = false;
        this.brewId = brew.id();
        this.totalTicks = Math.max(1, recipe.heatTimeTicks());
        this.remainingTicks = this.totalTicks;
        this.phase = CauldronPhase.BREWING;
        this.brewerId = brewerId;
        this.brewPoints = tier.brewPoints();
        this.spoilTicksWithoutHeat = 0;
        this.stirCount = 0;
        setChangedAndSync();
        return StartResult.STARTED;
    }

    /**
     * Swap what is heating without disturbing the clock. The Occamy eggshell's whole mechanic.
     *
     * @return false when this cauldron is not brewing anything
     */
    public boolean retarget(String newBrewId) {
        if (phase != CauldronPhase.BREWING) {
            return false;
        }
        this.brewId = newBrewId;
        setChangedAndSync();
        return true;
    }

    /**
     * Take the finished brew out, and hand the OWL credit to whoever started it.
     *
     * <p>Credit goes to the <b>brewer</b>, not to whoever holds the bottle. A cauldron that paid out
     * to the collector would make stealing somebody's finished brew pay twice.
     */
    public @Nullable Brew collect() {
        if (phase != CauldronPhase.DONE || brewId == null) {
            return null;
        }
        Brew brew = Brews.byId(brewId);
        awardBrewPoints();
        resetToIdle();
        return brew;
    }

    /** Empty a ruined batch. Gives nothing back. */
    public void discardSpoiled() {
        if (phase == CauldronPhase.SPOILED) {
            resetToIdle();
        }
    }

    private void resetToIdle() {
        phase = CauldronPhase.IDLE;
        brewId = null;
        remainingTicks = 0;
        totalTicks = 0;
        brewerId = null;
        brewPoints = 0;
        spoilTicksWithoutHeat = 0;
        stirCount = 0;
        penalties = 0f;
        catalystAdded = false;
        recipeId = null;
        // The water goes with the brew. A pot you have just poured out of is an empty pot, and
        // leaving it filled would let one bucket serve every brew a cauldron ever makes.
        filled = false;
        items.clear();
        setChangedAndSync();
    }

    private void awardBrewPoints() {
        if (brewPoints <= 0 || brewerId == null || level == null || level.getServer() == null) {
            return;
        }
        ServerPlayer brewer = level.getServer().getPlayerList().getPlayer(brewerId);
        if (brewer == null) {
            return;
        }
        PlayerSkillData skillData = brewer.getData(ModAttachments.SKILL_DATA.get());
        // A clear head is worth ten percent of what you study. The fractional part is paid as a
        // chance, because brew points are ones and twos — see HogwartsComfort#scaleStudy.
        skillData.addPotionBrewPoints(
                at.koopro.wizardsandbeasts.pumpkinjuice.HogwartsComfort.scaleStudy(brewer, brewPoints));
    }

    // -- the clock -------------------------------------------------------------------------------

    /**
     * One tick of a working cauldron.
     *
     * <p>Heat is checked every tick rather than only at the start, which is what makes the fire part
     * of the process instead of part of the paperwork. Losing it does not cancel immediately —
     * a brew off the boil is recoverable for {@link #SPOIL_TICKS_WITHOUT_HEAT} — but it is no longer
     * free, which the old pause-forever behaviour made it.
     */
    public static void serverTick(@NonNull Level level, @NonNull BlockPos pos,
                                  @NonNull BlockState state, @NonNull CauldronBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || !be.phase.isActive()) {
            return;
        }

        if (!CauldronHeat.hasHeatSource(serverLevel, pos.below())) {
            be.spoilTicksWithoutHeat++;
            if (be.spoilTicksWithoutHeat >= SPOIL_TICKS_WITHOUT_HEAT) {
                be.spoil(serverLevel, pos);
            } else if (be.spoilTicksWithoutHeat % 40 == 0) {
                // Only sync on the beat, not every tick: this is a cosmetic countdown and a packet
                // twenty times a second per cold cauldron is a real cost for a warning.
                be.setChangedAndSync();
            }
            return;
        }

        if (be.spoilTicksWithoutHeat != 0) {
            be.spoilTicksWithoutHeat = 0;
            be.setChangedAndSync();
        }

        be.remainingTicks--;
        if (be.remainingTicks <= 0) {
            be.remainingTicks = 0;
            if (be.rollFailure(serverLevel)) {
                be.spoil(serverLevel, pos);
                return;
            }
            be.phase = CauldronPhase.DONE;
            serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.1f);
            be.runBrewCompleteComponents(serverLevel);
            be.setChangedAndSync();
            return;
        }
        // Progress is a smooth number and the client interpolates nothing, so it is synced on a beat
        // rather than per tick. Twice a second is enough for a bar and a bubble rate.
        if (be.remainingTicks % 10 == 0) {
            be.setChangedAndSync();
        } else {
            be.setChanged();
        }
    }

    /**
     * Fire any {@code on_brew_complete} components, at the moment the pot finishes.
     *
     * <p>The subject is the <b>brewer</b>, and only if they are online and in this world — a
     * component that acts on somebody has to have somebody to act on, and a brewer who logged out an
     * hour ago is not standing over the cauldron. A brew whose components are all {@code on_drink}
     * costs nothing here: {@link BrewEffectEntry#hasPhase} short-circuits before anything is built.
     */
    private void runBrewCompleteComponents(ServerLevel level) {
        Brew brew = Brews.byId(brewId);
        if (brew == null
                || !BrewEffectEntry.hasPhase(brew.components(), BrewEffectPhase.ON_BREW_COMPLETE)) {
            return;
        }
        if (brewerId == null || level.getServer() == null) {
            return;
        }
        ServerPlayer brewer = level.getServer().getPlayerList().getPlayer(brewerId);
        if (brewer == null || brewer.level() != level) {
            return;
        }
        BrewEffectEntry.run(brew.components(),
                BrewEffectContext.onBrewComplete(brew, brewer, level));
    }

    /**
     * Whether this batch is ruined at the moment it would otherwise be finished.
     *
     * <p>Rolled <b>once</b>, at completion, rather than continuously — a per-tick chance would make a
     * long brew riskier than a short one for no reason a player could reason about, and would make
     * {@code heatTimeTicks} a difficulty knob by accident.
     *
     * <p>A recipe with no declared difficulty and nothing gone wrong in the pot skips the roll
     * entirely, so an ordinary potion is exactly as reliable as it was before failure existed.
     */
    private boolean rollFailure(ServerLevel level) {
        BrewingRecipe recipe = recipeId == null ? null : BrewingRecipes.byId(recipeId);
        float base = recipe == null ? 0f : recipe.failureChance();

        // A catalyst that was never added is charged for here rather than when its window closed,
        // because the brewer can still be told about it in the same breath as the result.
        float missed = penalties;
        if (recipe != null && recipe.catalyst().isPresent() && !catalystAdded) {
            missed += recipe.catalyst().get().missPenalty();
        }
        if (base <= 0f && missed <= 0f) {
            return false;
        }

        ServerPlayer brewer = brewerId == null || level.getServer() == null
                ? null : level.getServer().getPlayerList().getPlayer(brewerId);
        float chance = BrewFailure.chanceFor(brewer, base, missed);
        boolean failed = level.getRandom().nextFloat() < chance;
        if (failed && brewer != null) {
            at.koopro.wizardsandbeasts.feedback.PlayerFeedback.toast(brewer,
                    at.koopro.wizardsandbeasts.feedback.NoticeKind.FAIL,
                    net.minecraft.network.chat.Component.translatable(
                            "brew.wizards_and_beasts.failed.title"),
                    net.minecraft.network.chat.Component.translatable(
                            "brew.wizards_and_beasts.failed.body"));
        }
        return failed;
    }

    /** What happened when something was dropped into a working pot. */
    public enum TimedAddResult {
        /** The catalyst, in its window. */
        CATALYST_ACCEPTED,
        /** The catalyst, but too early or too late. */
        CATALYST_MISTIMED,
        /** Already added; a second one does nothing and costs nothing. */
        CATALYST_ALREADY,
        /** Something that does not belong in this brew. */
        CONTAMINATED
    }

    /**
     * Drop something into a pot that is already brewing.
     *
     * <p>This is what makes a brew a process rather than a wait. Nothing here ruins a batch outright:
     * every outcome moves the failure chance, and the roll still happens at the end. Being able to
     * make a brew *probably* fail and then watch it succeed anyway is a better story than being told
     * immediately that you have wasted ten minutes.
     *
     * <p>Contamination is charged per item, so emptying a stack of gravel into somebody's cauldron
     * costs them dearly — but each individual click is survivable, which is the difference between a
     * mistake and a griefing tool.
     */
    public TimedAddResult timedAdd(ItemStack stack) {
        BrewingRecipe recipe = recipeId == null ? null : BrewingRecipes.byId(recipeId);
        var catalyst = recipe == null ? java.util.Optional.<BrewingRecipe.Catalyst>empty()
                : recipe.catalyst();

        if (catalyst.isPresent() && stack.is(catalyst.get().item())) {
            if (catalystAdded) {
                return TimedAddResult.CATALYST_ALREADY;
            }
            if (catalyst.get().acceptsAt(progress())) {
                catalystAdded = true;
                setChangedAndSync();
                return TimedAddResult.CATALYST_ACCEPTED;
            }
            // Mistimed, not wasted: the penalty is the miss penalty rather than contamination, and
            // the catalyst counts as spent so a second attempt cannot fish for the window.
            catalystAdded = true;
            penalties += catalyst.get().missPenalty();
            setChangedAndSync();
            return TimedAddResult.CATALYST_MISTIMED;
        }

        penalties += CONTAMINATION_PENALTY;
        setChangedAndSync();
        return TimedAddResult.CONTAMINATED;
    }

    /** How much one wrong item costs. A quarter, so two mistakes are recoverable and four are not. */
    public static final float CONTAMINATION_PENALTY = 0.25f;

    /** The failure chance as it currently stands, for tooltips and tests. */
    public float accumulatedPenalties() {
        return penalties;
    }

    public boolean isCatalystAdded() {
        return catalystAdded;
    }

    public @Nullable String recipeId() {
        return recipeId;
    }

    /** How long this pot has been off the heat. Read by the inspector command. */
    public int spoilTicksWithoutHeatForDebug() {
        return spoilTicksWithoutHeat;
    }

    private void spoil(ServerLevel level, BlockPos pos) {
        phase = CauldronPhase.SPOILED;
        remainingTicks = 0;
        brewId = null;
        brewerId = null;
        brewPoints = 0;
        spoilTicksWithoutHeat = 0;
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 0.5f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 24, 0.25, 0.2, 0.25, 0.02);
        setChangedAndSync();
    }

    // -- anti-spam -------------------------------------------------------------------------------

    /**
     * Whether this interaction is far enough from the last one to act on.
     *
     * <p>A right-click on a block fires on both hands in some situations and repeats while the button
     * is held. Without a floor, holding the button empties a stack into the pot one item per tick,
     * which is neither the intended feel nor something a player can stop precisely.
     */
    public boolean acceptInteraction(long gameTime, int minimumGap) {
        if (gameTime - lastInteractGameTime < minimumGap && gameTime >= lastInteractGameTime) {
            return false;
        }
        lastInteractGameTime = gameTime;
        setChanged();
        return true;
    }

    /**
     * Take the recipe's ingredients out of the real slots.
     *
     * <p>Copy out, consume, copy back — rather than handing {@code consumeFrom} a container built
     * over {@code items.toArray()}. That array is a <em>copy</em>: {@code NonNullList.toArray}
     * allocates, so a {@link SimpleContainer} wrapping it writes to nothing the cauldron owns, the
     * ingredients survive the brew, and every potion becomes free. The bug is invisible from the
     * outside because the brew still starts and still finishes.
     */
    private void consumeFromSlots(BrewingRecipe recipe) {
        SimpleContainer working = new SimpleContainer(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            working.setItem(i, items.get(i).copy());
        }
        recipe.consumeFrom(working);
        for (int i = 0; i < SLOTS; i++) {
            items.set(i, working.getItem(i));
        }
    }

    // -- persistence -----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("phase", Codec.STRING, phase.getSerializedName());
        output.store("filled", Codec.BOOL, filled);
        output.store("remainingTicks", Codec.INT, remainingTicks);
        output.store("totalTicks", Codec.INT, totalTicks);
        output.store("brewPoints", Codec.INT, brewPoints);
        output.store("spoilTicksWithoutHeat", Codec.INT, spoilTicksWithoutHeat);
        output.store("stirCount", Codec.INT, stirCount);
        output.store("penalties", Codec.FLOAT, penalties);
        output.store("catalystAdded", Codec.BOOL, catalystAdded);
        if (recipeId != null) {
            output.store("recipeId", Codec.STRING, recipeId);
        }
        if (brewId != null) {
            output.store("brewId", Codec.STRING, brewId);
        }
        if (brewerId != null) {
            output.store("brewerId", UUIDUtil.CODEC, brewerId);
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                output.store("item" + i, ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        phase = CauldronPhase.byName(
                input.read("phase", Codec.STRING).orElse(CauldronPhase.IDLE.getSerializedName()),
                CauldronPhase.IDLE);
        filled = input.read("filled", Codec.BOOL).orElse(false);
        remainingTicks = input.read("remainingTicks", Codec.INT).orElse(0);
        totalTicks = input.read("totalTicks", Codec.INT).orElse(0);
        brewPoints = input.read("brewPoints", Codec.INT).orElse(0);
        spoilTicksWithoutHeat = input.read("spoilTicksWithoutHeat", Codec.INT).orElse(0);
        stirCount = input.read("stirCount", Codec.INT).orElse(0);
        penalties = input.read("penalties", Codec.FLOAT).orElse(0f);
        catalystAdded = input.read("catalystAdded", Codec.BOOL).orElse(false);
        recipeId = input.read("recipeId", Codec.STRING).orElse(null);
        brewId = input.read("brewId", Codec.STRING).orElse(null);
        brewerId = input.read("brewerId", UUIDUtil.CODEC).orElse(null);
        items.clear();
        for (int i = 0; i < SLOTS; i++) {
            items.set(i, input.read("item" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        }

        // A brew whose id no longer resolves cannot finish, and leaving it BREWING would tick a
        // cauldron forever towards a bottle that cannot be made. Datapacks change between sessions;
        // this is the honest outcome for a recipe that was removed under a running pot.
        if (phase.isActive() && (brewId == null || Brews.byId(brewId) == null)) {
            phase = CauldronPhase.SPOILED;
            brewId = null;
        }
    }

    // -- client sync -----------------------------------------------------------------------------

    /**
     * Everything the client needs and nothing it does not.
     *
     * <p>The full save is sent rather than a hand-picked subset. The block entity is small, it
     * changes on a beat rather than per tick, and the alternative — two serialisation formats for one
     * object — is how a field ends up saved but not synced and a cauldron renders idle while it
     * brews.
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level == null || level.isClientSide()) {
            return;
        }
        refreshVisual();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    /**
     * Push the pot's condition into its blockstate, so it is visible without clicking it.
     *
     * <p>Derived here rather than set by each caller: every transition already funnels through
     * {@link #setChangedAndSync()}, so hanging the projection off that one place means a new phase
     * cannot be added and forgotten. Writes only on an actual change — {@code setBlock} on an
     * unchanged state is a needless block update and a needless client packet.
     */
    private void refreshVisual() {
        if (level == null) {
            return;
        }
        BlockState current = getBlockState();
        if (!current.hasProperty(CauldronVisual.PROPERTY)) {
            return;
        }
        CauldronVisual wanted = CauldronVisual.of(phase, filled, !isEmptyOfIngredients());
        if (current.getValue(CauldronVisual.PROPERTY) != wanted) {
            // Flag 3 = update neighbours + tell clients. No flag 8: this is not a state a comparator
            // or an observer should treat as a redstone event on every ingredient.
            level.setBlock(getBlockPos(), current.setValue(CauldronVisual.PROPERTY, wanted), 3);
        }
    }

    /** The brewer, for tests and tooltips. */
    public Optional<UUID> brewerId() {
        return Optional.ofNullable(brewerId);
    }
}
