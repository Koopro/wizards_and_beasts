package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.demiguise.CloakCharges;
import at.koopro.wizardsandbeasts.demiguise.Demiguise;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A single hair from a Demiguise, which is very hard to keep hold of.
 *
 * <p>Crouch and use it and the hair fades out of your hand over
 * {@link Demiguise#HAIR_FADE_TICKS} ticks, taking you with it: five seconds invisible, and five
 * seconds during which nothing will pick you as a target unless you give it a reason.
 *
 * <h2>The fade is real, not a message</h2>
 * The hair is <em>held</em> while it goes, and the item model dispatches over
 * {@code minecraft:use_duration} through {@link Demiguise#FADE_FRAMES} progressively thinner
 * sprites. That is why this is a use-over-time item rather than an instant click: an instant one
 * could only ever have said "the hair fades" in a chat line, and the whole idea is that you watch it
 * happen in your own hand.
 *
 * <h2>Crouch to spend, stand to keep</h2>
 * Requiring the crouch is not decoration either. This item is also a crafting reagent for cloak
 * charges and the Weave trim, and a rare reagent that vanishes on an ordinary right-click would be
 * lost by every player at least once.
 */
@NullMarked
public class DemiguiseHairItem extends Item {

    public DemiguiseHairItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            // Standing up, the hair is a material rather than a charm: weave it into a cloak.
            return weaveIntoCloak(player, hand, stack);
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /**
     * Weaves one hair into an Invisibility Cloak — worn, or held in the other hand.
     *
     * <p>An interaction rather than a crafting recipe on purpose. A shapeless recipe cannot read the
     * cloak that went in, so it would hand back a cloak at the default charge and silently throw away
     * whatever was left on the old one; getting that right needs a custom recipe serializer for
     * something the hair can already do in the hand. Doing it here also means the player sees the
     * number go up.
     *
     * <p>Passes through for a Deathly Hallow: it has nothing to top up, and eating a rare hair to
     * tell somebody that would be worse than doing nothing.
     */
    private static InteractionResult weaveIntoCloak(Player player, InteractionHand hand, ItemStack hair) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack cloak = player.getItemInHand(other);
        if (!CloakCharges.isChargeable(cloak)) {
            cloak = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        }
        if (!CloakCharges.isChargeable(cloak)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (CloakCharges.remaining(cloak) >= CloakCharges.MAX_CHARGES) {
            if (player instanceof ServerPlayer server) {
                at.koopro.wizardsandbeasts.feedback.PlayerFeedback.actionBar(server,
                        Component.translatable("item.wizards_and_beasts.demiguise_hair.cloak_full")
                                .withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }

        int total = CloakCharges.recharge(cloak, 1);
        hair.consume(1, player);
        player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.7f);
        if (player instanceof ServerPlayer server) {
            at.koopro.wizardsandbeasts.feedback.PlayerFeedback.actionBar(server,
                    Component.translatable("item.wizards_and_beasts.demiguise_hair.woven", total)
                            .withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Demiguise.HAIR_FADE_TICKS;
    }

    /**
     * {@link ItemUseAnimation#BOW} rather than {@code EAT} or {@code DRINK}: the hand goes still and
     * holds the hair up instead of miming a mouthful, and nothing about this is eating.
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide()) {
            return stack;
        }
        Demiguise.conceal(entity, Demiguise.HAIR_INVISIBILITY_TICKS, Demiguise.HAIR_CAMOUFLAGE_TICKS);
        Demiguise.vanishEffects(entity);

        if (entity instanceof Player player) {
            // The cooldown is set on the item, so it covers every hair in the inventory rather than
            // the one stack that happened to be used.
            player.getCooldowns().addCooldown(stack, Demiguise.HAIR_COOLDOWN_TICKS);
            if (entity instanceof ServerPlayer server) {
                at.koopro.wizardsandbeasts.feedback.PlayerFeedback.actionBar(server,
                        Component.translatable("item.wizards_and_beasts.demiguise_hair.used")
                                .withStyle(ChatFormatting.GRAY));
            }
            stack.consume(1, player);
        } else {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.demiguise_hair.use",
                        Demiguise.HAIR_INVISIBILITY_TICKS / 20,
                        Demiguise.HAIR_COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.demiguise_hair.craft")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
