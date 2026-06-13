package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.WandComponents;
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
        if (WandComponents.getWood(stack) != null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Identifier wood = wandWoodFromLogBlock(state);
        if (wood == null) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
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
     * Maps vanilla log families to wand wood datapack ids (see {@code data/wizards_and_beasts/wizards_and_beasts/wand_woods}).
     */
    private static Identifier wandWoodFromLogBlock(BlockState state) {
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        Identifier wood = WandComponents.getWood(stack);
        if (wood == null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.wand_blank.tooltip.no_wood"));
        } else {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.wand_blank.tooltip.wood", wood));
        }
    }
}
