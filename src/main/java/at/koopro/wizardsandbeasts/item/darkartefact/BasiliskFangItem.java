package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.effect.BasiliskVenomEffect;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A basilisk fang, still wet with venom.
 *
 * <ul>
 *   <li><b>A stab carries the venom</b> ({@link BasiliskVenomEffect#inject}) and wears the fang
 *       ({@link #DOSES} doses before it is dry).</li>
 *   <li><b>It ends a Horcrux.</b> Fang in the main hand, the Horcrux in the other, use: Harry drove one through the
 *       diary, Hermione through the cup. The fang is spent. The release itself is {@link HorcruxDestruction}.</li>
 * </ul>
 */
@NullMarked
public final class BasiliskFangItem extends Item {

    /** Venom doses in one fang. */
    public static final int DOSES = 6;

    public BasiliskFangItem(Properties properties) {
        super(properties.durability(DOSES));
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide() && target.isAlive()) {
            BasiliskVenomEffect.inject(target, attacker);
        }
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND);
        if (hand != InteractionHand.MAIN_HAND || !HorcruxDestruction.isIntact(other)) {
            return InteractionResult.PASS;
        }
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server && HorcruxDestruction.destroy(server, player, other)) {
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.wizards_and_beasts.basilisk_fang.venom",
                stack.getMaxDamage() - stack.getDamageValue()).withStyle(ChatFormatting.DARK_GREEN));
        tooltip.accept(Component.translatable("item.wizards_and_beasts.basilisk_fang.horcrux")
                .withStyle(ChatFormatting.GRAY));
    }
}
