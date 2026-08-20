package at.koopro.wizardsandbeasts.item.wand;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class WandBlankItem extends Item {
    public WandBlankItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (WandComponents.getWood(stack) != null) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Identifier wood = wandWoodFromLogBlock(state);
        if (wood == null) {
            return InteractionResult.PASS;
        }
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        stack.set(WandComponents.WAND_WOOD.get(), wood);
        player.setItemInHand(context.getHand(), stack);
        level.playSound(null, pos, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.PLAYERS, 0.5f, 1.35f);
        return InteractionResult.SUCCESS;
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
