package at.koopro.wizardsandbeasts.item.wearable;

import at.koopro.wizardsandbeasts.mandrake.MandrakeScream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Fluffy pink earmuffs — the first thing Professor Sprout hands out before anyone touches a Mandrake.
 *
 * <p>Equips to the head, exactly like the {@link BlindfoldItem} it sits beside, and is the intended
 * answer to {@link MandrakeScream}. Membership in {@code muffles_screams} is what actually spares the
 * wearer; this class only puts them on.
 *
 * <p>They protect the ears and nothing else, which is the point: wearing them costs you the helmet
 * slot for the length of a harvest, and a wizard who forgets to take them off afterwards is walking
 * around a hostile world in a pair of earmuffs.
 */
@NullMarked
public class EarmuffsItem extends Item {

    public EarmuffsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        ItemStack onHead = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!onHead.isEmpty()) {
            return InteractionResult.PASS;
        }

        player.setItemSlot(EquipmentSlot.HEAD, inHand.copyWithCount(1));
        inHand.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(this));
        player.playSound(SoundEvents.WOOL_PLACE, 1.0f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.earmuffs.protects")
                .withStyle(ChatFormatting.GRAY));
    }
}
