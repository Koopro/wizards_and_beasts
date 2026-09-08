package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
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
import net.minecraft.world.phys.EntityHitResult;

import java.util.function.Consumer;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;

/**
 * The Dark Mark brand. Use on yourself to take the Mark; use on another player to brand them;
 * sneak-use while marked to call every marked wizard online — the Mark burns and announces the
 * caller's location, the summons Voldemort used to gather his Death Eaters.
 * <p>
 * The marked state is the {@link ModAttachments#DARK_MARK} attachment. Gated behind
 * {@link Module#DARK_ARTS}.
 */
public class DarkMarkItem extends Item {

    public DarkMarkItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("The Dark Mark. Burned into the left forearm.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("It burned when He was near.")
                .withStyle(ChatFormatting.DARK_RED));
        tooltipAdder.accept(Component.literal("Sneak-use to call the marked").withStyle(ChatFormatting.BLUE));
        tooltipAdder.accept(Component.literal("Source: Goblet of Fire onward")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    /**
     * How long it takes to burn the Mark into someone.
     *
     * <p>Two seconds, and the point of them is that the victim gets to see it coming. Branding used
     * to be instantaneous, with no cast time, no cooldown and nothing consumed — you could walk past
     * a player and mark them permanently before they knew you were there. A brand that has to be
     * held is one that can be walked out of, which is the difference between an attack and a fact.
     */
    public static final int BRAND_TICKS = 40;

    /**
     * Starts branding whoever was clicked.
     *
     * <p>Nothing is decided here. The victim is re-found when the brand lands — see
     * {@link #finishUsingItem} — so this only has to agree on both sides that a brand has begun.
     * Even the already-marked refusal waits, because the {@code DARK_MARK} attachment is server-side
     * and a client that guessed would start an animation the server never agreed to.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (!(target instanceof Player)) {
            return InteractionResult.PASS;
        }
        // CONSUME rather than SUCCESS: SUCCESS swings the arm, and a swing at the start of a brand
        // reads as the brand having already landed.
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return BRAND_TICKS;
    }

    /**
     * {@link ItemUseAnimation#NONE} — the reach belongs to {@code ItemUsePosePass}.
     *
     * <p>Only the branding path ever starts a use; {@link #use} resolves instantly in all three of
     * its branches, so this is never consulted for taking the Mark or calling through it.
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * Burns the Mark into whoever is still under the crosshair.
     *
     * <p><b>Re-found, not remembered.</b> The victim is picked again along the caster's look vector
     * at the moment the brand lands, which is the whole reason the channel is worth having: a player
     * who steps behind a wall, or simply walks away, is not branded. Remembering the target from the
     * first click would have made the two seconds decorative.
     *
     * <p>Uses {@link BeamRayResolver}, which picks the nearer of block and entity — so a victim who
     * gets a door between themselves and the caster escapes exactly as they should.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return stack;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return stack;
        }
        BeamRay ray = BeamRayResolver.resolve(player, 1.0f,
                (float) player.entityInteractionRange(), BeamRayResolver.LIVING_FILTER);
        if (!ray.hitsEntity() || !(ray.hit() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer victim)) {
            player.displayClientMessage(Component.literal("The Mark finds nobody.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return stack;
        }
        if (victim.getData(ModAttachments.DARK_MARK.get())) {
            player.displayClientMessage(Component.literal(victim.getName().getString() + " already bears the Mark.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return stack;
        }
        victim.setData(ModAttachments.DARK_MARK.get(), true);
        PlayerFeedback.toast(victim, NoticeKind.WARN,
                Component.translatable("item.wizards_and_beasts.dark_mark.branded.title"),
                Component.translatable("item.wizards_and_beasts.dark_mark.branded.body"));
        player.displayClientMessage(Component.literal("You brand " + victim.getName().getString() + " with the Dark Mark.")
                .withStyle(ChatFormatting.DARK_RED), true);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer caster) || caster.level().getServer() == null) {
            return InteractionResult.PASS;
        }
        boolean marked = caster.getData(ModAttachments.DARK_MARK.get());

        if (caster.isShiftKeyDown()) {
            if (!marked) {
                caster.displayClientMessage(Component.literal("You bear no Mark to call through.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
                return InteractionResult.SUCCESS;
            }
            int called = 0;
            for (ServerPlayer other : caster.level().getServer().getPlayerList().getPlayers()) {
                if (other != caster && other.getData(ModAttachments.DARK_MARK.get())) {
                    PlayerFeedback.toast(other, NoticeKind.WARN,
                            Component.translatable("item.wizards_and_beasts.dark_mark.summoned.title"),
                            Component.translatable("item.wizards_and_beasts.dark_mark.summoned.body",
                                    caster.getName(),
                                    caster.getBlockX() + ", " + caster.getBlockY() + ", " + caster.getBlockZ()));
                    called++;
                }
            }
            caster.displayClientMessage(Component.literal("You call through the Mark. " + called
                    + (called == 1 ? " answers." : " answer.")).withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResult.SUCCESS;
        }

        if (marked) {
            caster.displayClientMessage(Component.literal("The Mark is already yours.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return InteractionResult.SUCCESS;
        }
        caster.setData(ModAttachments.DARK_MARK.get(), true);
        PlayerFeedback.toast(caster, NoticeKind.WARN,
                Component.translatable("item.wizards_and_beasts.dark_mark.taken.title"),
                Component.translatable("item.wizards_and_beasts.dark_mark.taken.body"));
        return InteractionResult.SUCCESS;
    }
}
