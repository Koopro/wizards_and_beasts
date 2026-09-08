package at.koopro.wizardsandbeasts.item.wand;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * A wand blank, and the log it is shaped against.
 *
 * <h2>Shaping is a channel, not a click</h2>
 *
 * <p>Choosing the wood is the wandmaking beat of the mod — the one moment a player picks the thing
 * their wand will be — and it used to resolve in a single tick with one soft knock. It now takes
 * {@value #CARVE_TICKS} ticks of held right-click against the log, with the pose layer whittling the
 * blank and a chip of the log flying every stroke.
 *
 * <h2>The target is re-found every tick rather than remembered</h2>
 *
 * <p>{@code useOn} knows the clicked block, but {@code onUseTick} and {@code finishUsingItem} do not,
 * and the obvious fix — stash the {@code BlockPos} somewhere for the duration — buys a second problem
 * for free: state that has to be cleaned up on logout, on death, on dropping the blank mid-carve.
 *
 * <p>Re-picking instead is both simpler and a better mechanic. Looking away from the log cancels the
 * carve, because looking away genuinely stops the blank from being held against it, and the player
 * needs no explanation of that rule. It also means the carve cannot survive the log being broken,
 * replaced or turned into something else half way through.
 */
public class WandBlankItem extends Item {

    /** Two and a half seconds of whittling. Long enough to be a decision, short of being a chore. */
    public static final int CARVE_TICKS = 50;

    /**
     * One stroke of the knife. The pose layer runs the arm on this same period, so the sound and the
     * chip land with the arm at the bottom of its travel rather than on some unrelated beat.
     */
    public static final int STROKE_TICKS = 10;

    public WandBlankItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** A log this blank could be shaped from, and the wand wood it would yield. */
    private record CarveTarget(BlockPos pos, BlockState state, Identifier wood) {}

    /**
     * The log the player is looking at, or null if there is nothing to carve against.
     *
     * <p>Also the guard for a blank that already has a wood: a shaped blank passes through to
     * whatever else wants the click rather than being re-carved into a second species.
     */
    private static @Nullable CarveTarget targetUnderCrosshair(Level level, Player player, ItemStack stack) {
        if (WandComponents.getWood(stack) != null) {
            return null;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return targetAt(level, block.getBlockPos());
    }

    private static @Nullable CarveTarget targetAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Identifier wood = wandWoodFromLogBlock(state);
        return wood == null ? null : new CarveTarget(pos, state, wood);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player == null || WandComponents.getWood(stack) != null) {
            return InteractionResult.PASS;
        }
        if (targetAt(context.getLevel(), context.getClickedPos()) == null) {
            return InteractionResult.PASS;
        }
        // Started on both sides: the client needs the use to begin for the pose and the held-item
        // animation, the server for everything else. CONSUME rather than SUCCESS because SUCCESS
        // swings the arm, and an arm that swings at the start of a carve reads as the carve having
        // already happened.
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return CARVE_TICKS;
    }

    /**
     * {@link ItemUseAnimation#NONE}, because the pose belongs to {@code ItemUsePosePass}.
     *
     * <p>None of vanilla's five animations is a whittle — {@code EAT} and {@code DRINK} raise the
     * blank to the mouth, {@code BOW} and {@code BLOCK} hold it still — and the same reasoning the
     * wand already follows applies here: an approximate vanilla animation is worse than none, since
     * it has to be undone by the pose pass before the real pose can be written.
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }
        CarveTarget target = targetUnderCrosshair(level, player, stack);
        if (target == null) {
            // Looked away, or the log is gone. Stopping the use rather than letting it run out means
            // finishUsingItem never fires, so there is no second place that has to re-check this.
            entity.stopUsingItem();
            return;
        }
        int elapsed = CARVE_TICKS - remainingTicks;
        if (elapsed > 0 && elapsed % STROKE_TICKS == 0 && level instanceof ServerLevel server) {
            stroke(server, player, target);
        }
    }

    /** One pass of the knife: a knock off the log and a chip of its own wood. */
    private static void stroke(ServerLevel level, Player player, CarveTarget target) {
        level.playSound(null, target.pos(), SoundEvents.BAMBOO_WOOD_HIT, SoundSource.PLAYERS,
                0.4f, 1.25f);
        Vec3 centre = Vec3.atCenterOf(target.pos());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target.state()),
                centre.x, centre.y, centre.z, 6, 0.25, 0.25, 0.25, 0.0);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return stack;
        }
        CarveTarget target = targetUnderCrosshair(level, player, stack);
        if (target == null) {
            return stack;
        }
        stack.set(WandComponents.WAND_WOOD.get(), target.wood());
        player.setItemInHand(player.getUsedItemHand(), stack);
        level.playSound(null, target.pos(), SoundEvents.BAMBOO_WOOD_HIT, SoundSource.PLAYERS,
                0.5f, 1.35f);
        return stack;
    }

    /**
     * Maps a log block to a wand wood datapack id (see
     * {@code data/wizards_and_beasts/wizards_and_beasts/wand_woods}).
     *
     * <p><b>The mod's own wandwood logs are checked first.</b> They have to be: this method knew only
     * vanilla tags, so every one of the nine species the mod actually grows fell straight through to
     * {@code null} and a blackthorn log could not be shaped into a blackthorn wand. The species that
     * the whole wandwood worldgen pass exists to plant were the only ones a blank refused.
     *
     * <p>Driven off {@link ModBlocks#ALL_WOOD_SETS} rather than a hand-written table, so a species
     * added there is shapeable the day it registers — a hardcoded list is precisely what failed here.
     * All four pillar variants count, because vanilla's {@code *_LOGS} tags already cover log, wood,
     * stripped log and stripped wood, and matching fewer would make the mod's own timber pickier than
     * the vanilla it stands in for.
     *
     * <p>The vanilla families below stay as they are: they are the fallback that lets a player shape a
     * blank before finding a wandwood tree. Note their donor choices no longer line up with what the
     * regenerated textures made each species look like (vanilla dark oak yields yew, while the log
     * that now <em>looks</em> like dark oak is blackthorn) — deliberately left alone here, since
     * retuning them changes what existing worlds produce.
     */
    private static @Nullable Identifier wandWoodFromLogBlock(BlockState state) {
        for (WoodSet set : ModBlocks.ALL_WOOD_SETS) {
            if (state.is(set.log().get()) || state.is(set.strippedLog().get())
                    || state.is(set.wood().get()) || state.is(set.strippedWood().get())) {
                return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, set.name());
            }
        }
        if (state.is(BlockTags.OAK_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "rowan");
        }
        if (state.is(BlockTags.PALE_OAK_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "rowan");
        }
        if (state.is(BlockTags.SPRUCE_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "holly");
        }
        if (state.is(BlockTags.BIRCH_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hawthorn");
        }
        if (state.is(BlockTags.JUNGLE_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "walnut");
        }
        if (state.is(BlockTags.ACACIA_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ash");
        }
        if (state.is(BlockTags.DARK_OAK_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "yew");
        }
        if (state.is(BlockTags.MANGROVE_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "willow");
        }
        if (state.is(BlockTags.CHERRY_LOGS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "blackthorn");
        }
        if (state.is(BlockTags.CRIMSON_STEMS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "elder");
        }
        if (state.is(BlockTags.WARPED_STEMS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vine");
        }
        if (state.is(BlockTags.BAMBOO_BLOCKS)) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vine");
        }
        return null;
    }

    /**
     * <p><b>The argument must be a {@code Component} or a primitive, not the raw id.</b> {@code TranslatableContents}
     * accepts only a {@code Component}, {@code Number}, {@code Boolean} or {@code String} as an argument,
     * and in a development runtime its constructor <em>throws</em> on anything else rather than falling
     * back to {@code toString()} the way a packaged build does. Passing the raw {@link Identifier} here
     * meant that the moment a blank was shaped, hovering it — in the inventory, or in JEI's wandmaking
     * entry, which builds a shaped blank of its own — threw out of the tooltip build and took the client
     * with it. The blank looked like it could not accept a wood type at all.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        Identifier wood = WandComponents.getWood(stack);
        if (wood == null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.wand_blank.tooltip.no_wood"));
        } else {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.wand_blank.tooltip.wood",
                    WandLoreNames.wood(context.registries(), wood)));
        }
    }
}
