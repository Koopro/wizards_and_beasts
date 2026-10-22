package at.koopro.wizardsandbeasts.artefact.gryffindor;

import at.koopro.wizardsandbeasts.effect.BasiliskVenomEffect;
import at.koopro.wizardsandbeasts.item.darkartefact.HorcruxDestruction;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * The Sword of Gryffindor: goblin silver, so it never wears or tarnishes, and a netherite blade's weight.
 *
 * <p>"Goblin-made silver repels mundane dirt, imbibing only that which strengthens it." The blade that kills a
 * basilisk takes in its venom for good ({@link GryffindorEvents}); after that it carries the venom into what it
 * strikes, as a fang does, and -- with a Horcrux in the other hand -- ends the Horcrux. Unlike a fang it is not spent.
 */
@NullMarked
public class SwordOfGryffindorItem extends Item {

    public SwordOfGryffindorItem(Properties properties) {
        super(properties);
    }

    public static boolean imbued(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.VENOM_IMBUED.get()));
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (imbued(stack) && !attacker.level().isClientSide() && target.isAlive()) {
            BasiliskVenomEffect.inject(target, attacker);
        }
    }

    /** Main hand, a Horcrux in the other, use: as with the fang, but the sword is kept. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack other = player.getItemInHand(InteractionHand.OFF_HAND);
        if (hand != InteractionHand.MAIN_HAND || !imbued(player.getItemInHand(hand))
                || !HorcruxDestruction.isIntact(other)) {
            return InteractionResult.PASS;
        }
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            HorcruxDestruction.destroy(server, player, other);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return imbued(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (imbued(stack)) {
            tooltip.accept(Component.translatable("item.wizards_and_beasts.sword_of_gryffindor.venom")
                    .withStyle(ChatFormatting.DARK_GREEN));
            tooltip.accept(Component.translatable("item.wizards_and_beasts.sword_of_gryffindor.horcrux")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
