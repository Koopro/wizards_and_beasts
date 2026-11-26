package at.koopro.wizardsandbeasts.wand.bench;

import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import at.koopro.wizardsandbeasts.wand.registry.BenchEnhancerDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

public class WandmakersBenchBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** How often the server rechecks whether there is work on the bench. Twice a second is plenty. */
    private static final int WORK_CHECK_INTERVAL = 10;

    /** How often a cramped (solo) bench looks again for room to grow its side half. Every five seconds. */
    private static final int GROW_RETRY_INTERVAL = 100;

    /** Whether this solo bench has looked for room since it loaded; the first look is immediate. */
    private boolean growTried;

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(3);
    private float cachedTierScore = 0.0f;
    private List<BlockPos> detectedEnhancers = new ArrayList<>();
    private int selectedFlexibilityOrdinal = WandFlexibility.PLIANT.ordinal();

    /**
     * Whether there is a blank or a core sitting on the bench, i.e. whether it is mid-job.
     *
     * <p>Deliberately derived from the <em>contents</em>, not from whether somebody has the menu open.
     * The brief asked for "menu open or a wand being shaped"; contents is the durable half of that and
     * the menu half turns out to be the worse signal. A menu-open counter has to survive a player
     * disconnecting with the screen up or it leaks a permanently-working bench — vanilla needs
     * {@code ContainerOpenersCounter} for exactly this, and that hangs off {@code Container}, which
     * this bench does not use. Contents cannot leak, and it is visible to <em>everyone</em> rather
     * than only to the one player whose GUI is open, which is the point of animating a block at all.
     */
    private boolean working;

    /** The tools on the bench, as {@link BenchTool#bit()}s. Saved, and sent to clients so the rig can draw them. */
    private int tools;

    private static final int ALL_TOOLS = (1 << BenchTool.values().length) - 1;

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public WandmakersBenchBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.WANDMAKERS_BENCH.get(), pos, blockState);
    }

    // -- rendering -------------------------------------------------------------------------------

    public boolean isWorking() {
        return working;
    }

    /**
     * Recomputes {@link #working} and tells clients when it changes.
     *
     * <p>Polled rather than pushed because slot writes go straight through the
     * {@code ItemStacksResourceHandler} from the menu, with no single choke point to hook. Polling on
     * a ten-tick beat is cheap, cannot miss an edit, and cannot be bypassed by a route that forgets to
     * notify — which a hook on one of several write paths could.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WandmakersBenchBlockEntity bench) {
        if (state.getValue(WandmakersBenchBlock.PART) == BenchPart.SOLO
                && (!bench.growTried || level.getGameTime() % GROW_RETRY_INTERVAL == 0)) {
            bench.growTried = true;
            if (tryGrow(level, pos, state)) {
                return;
            }
        }
        if (level.getGameTime() % WORK_CHECK_INTERVAL != 0) {
            return;
        }
        boolean wanted = bench.hasWorkOnIt();
        if (wanted == bench.working) {
            return;
        }
        bench.working = wanted;
        bench.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    /**
     * Turns a solo bench into a pair when one of its four sides has room: its own facing first, so a bench that was
     * placed facing a room keeps facing it, then clockwise. The working part keeps its block entity and contents
     * (a state change of the same block keeps it). Returns whether it grew.
     */
    static boolean tryGrow(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(WandmakersBenchBlock.FACING);
        for (int turn = 0; turn < 4; turn++) {
            Direction f = facing;
            for (int i = 0; i < turn; i++) {
                f = f.getClockWise();
            }
            BlockPos side = WandmakersBenchBlock.sideOf(pos, f);
            if (!level.isLoaded(side) || !level.getWorldBorder().isWithinBounds(side)
                    || !level.getBlockState(side).canBeReplaced()) {
                continue;
            }
            BlockState main = state.setValue(WandmakersBenchBlock.FACING, f).setValue(WandmakersBenchBlock.PART, BenchPart.MAIN);
            level.setBlock(pos, main, 3);
            level.setBlock(side, main.setValue(WandmakersBenchBlock.PART, BenchPart.SIDE), 3);
            return true;
        }
        return false;
    }

    /** A blank or a core in the input slots. The output slot does not count — that job is finished. */
    private boolean hasWorkOnIt() {
        return !getSlotStack(0).isEmpty() || !getSlotStack(1).isEmpty();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Half a second of blend: unlike the cauldron's liquid surfaces, these two clips pose the same
        // bones, so easing between them reads as the wandmaker settling rather than as a snap.
        controllers.add(new AnimationController<>("bench", 10, state -> state.setAndContinue(
                working ? RawAnimation.begin().thenLoop("animation.wandmakers_bench.working")
                        : RawAnimation.begin().thenLoop("animation.wandmakers_bench.idle"))));
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        recalcTierScore();
    }

    public void recalcTierScore() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        HolderLookup.RegistryLookup<BenchEnhancerDefinition> lookup =
                serverLevel.registryAccess().lookupOrThrow(WandDatapackRegistries.BENCH_ENHANCER_REGISTRY);
        int radius = WandResonanceConfigLoader.getConfig(serverLevel.registryAccess()).scanRadius();
        BenchMultiblockScanner.ScanResult result =
                BenchMultiblockScanner.scanEnhancersWithPositions(serverLevel, worldPosition, lookup, radius);
        this.cachedTierScore = result.tierScore();
        this.detectedEnhancers = result.enhancers();
    }

    // -- tools ------------------------------------------------------------------------------------

    public boolean hasTool(BenchTool tool) {
        return (tools & tool.bit()) != 0;
    }

    /** The tools on the bench as a bit mask, for the renderer. */
    public int toolMask() {
        return tools;
    }

    /** Lays a tool on the bench. False when that tool is already there. */
    public boolean placeTool(BenchTool tool) {
        if (hasTool(tool)) {
            return false;
        }
        tools |= tool.bit();
        toolsChanged();
        return true;
    }

    /** Takes every tool off the bench and returns them as items, in {@link BenchTool} order. */
    public List<ItemStack> takeTools() {
        List<ItemStack> out = new ArrayList<>();
        for (BenchTool tool : BenchTool.values()) {
            if (hasTool(tool)) {
                out.add(toolStack(tool));
            }
        }
        if (tools != 0) {
            tools = 0;
            toolsChanged();
        }
        return out;
    }

    private static ItemStack toolStack(BenchTool tool) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, tool.itemId())));
    }

    private void toolsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * The bench is coming down: what is on it falls to the floor. The blank, the core and the tools fall. The output
     * slot is not dropped, because it is the preview of a wand not yet made, and dropping it would make a free wand.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = getSlotStack(slot);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        for (BenchTool tool : BenchTool.values()) {
            if (hasTool(tool)) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, toolStack(tool));
            }
        }
    }

    public int getSelectedFlexibilityOrdinal() {
        return selectedFlexibilityOrdinal;
    }

    public void setSelectedFlexibilityOrdinal(int selectedFlexibilityOrdinal) {
        int max = WandFlexibility.values().length - 1;
        this.selectedFlexibilityOrdinal = Math.max(0, Math.min(max, selectedFlexibilityOrdinal));
        setChanged();
    }

    public float getCachedTierScore() {
        return cachedTierScore;
    }

    public List<BlockPos> getDetectedEnhancers() {
        return detectedEnhancers;
    }

    public ItemStacksResourceHandler getInventory() {
        return inventory;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("flexibility_ordinal", Codec.INT, selectedFlexibilityOrdinal);
        // Absent when there are none, so a bench saved before tools existed and one with none read the same.
        if (tools != 0) {
            output.store("tools", Codec.INT, tools);
        }
        for (int i = 0; i < 3; i++) {
            // ItemStack.CODEC refuses an empty stack: storing one logged "Item must not be minecraft:air" on every
            // save and update packet. An absent slot reads back as empty, which is also how the client learns of it.
            ItemStack stack = getSlotStack(i);
            if (!stack.isEmpty()) {
                output.store("slot_" + i, ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        selectedFlexibilityOrdinal = input.read("flexibility_ordinal", Codec.INT).orElse(WandFlexibility.PLIANT.ordinal());
        for (int i = 0; i < 3; i++) {
            setSlotStack(i, input.read("slot_" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        }
        // Derived, not stored. getUpdateTag ships the slots anyway, so putting `working` on the wire
        // as well would be a second copy of the same fact — and the copy is what goes stale. This way
        // a client cannot disagree with the server about whether the bench is busy.
        working = hasWorkOnIt();
        // Read as a mask of known tools only: a bit a later build added and this one does not know is dropped.
        tools = input.read("tools", Codec.INT).orElse(0) & ALL_TOOLS;
    }

    private ItemStack getSlotStack(int slot) {
        int count = (int) inventory.getAmountAsLong(slot);
        if (count <= 0) return ItemStack.EMPTY;
        return inventory.getResource(slot).toStack(count);
    }

    private void setSlotStack(int slot, ItemStack stack) {
        inventory.set(slot, ItemResource.of(stack), stack.getCount());
    }
}
