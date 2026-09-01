package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.mandrake.MandrakeScream;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A Mandrake root, pulled and still shrieking.
 *
 * <p>Replantable. The root <em>is</em> the plant, so putting one back in the soil starts it growing
 * again from nothing — which is what a Herbology greenhouse actually does with them, and it means a
 * wizard who has one Mandrake never needs seeds again. Seeds remain the cheap way to start.
 *
 * <p>Deliberately not edible and not a throwable. The screaming is the Mandrake's whole character and
 * it belongs to two moments — {@code MandrakeCropBlock} when a mature one comes out of the ground,
 * and a thrown Baby Mandrake — so a third, cheaper way to set it off from the hotbar would flatten
 * both.
 */
@NullMarked
public class MandrakeItem extends Item {

    public MandrakeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos soil = context.getClickedPos();
        BlockPos above = soil.above();

        BlockState crop = ModBlocks.MANDRAKE_CROP.get().defaultBlockState();
        // canSurvive is the whole soil test: it is what the crop itself uses, so a Mandrake goes into
        // exactly the ground a Mandrake grows in, and stays right if that ever changes.
        if (!level.getBlockState(above).isAir() || !crop.canSurvive(level, above)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        level.setBlockAndUpdate(above, crop);
        level.playSound(null, above, net.minecraft.sounds.SoundEvents.CROP_PLANTED,
                SoundSource.BLOCKS, 1.0f, 0.8f);
        Player player = context.getPlayer();
        if (player != null) {
            context.getItemInHand().consume(1, player);
        } else {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.mandrake.plant")
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.mandrake.scream",
                        (int) MandrakeScream.ADULT_RADIUS)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
