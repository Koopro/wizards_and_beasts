package at.koopro.wizardsandbeasts.event.item;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Occamy shells do not survive a bad landing.
 *
 * <p>Thin silver, carried in the hand. A drop that hurts you hurts them, and roughly a third of the
 * time the whole handful goes. That is the cost of the material: an Occamy shell is a thing you have
 * to <em>carry home</em>, and a wizard who takes a shortcut off a cliff with a stack of them is
 * making a choice.
 *
 * <h2>Held, not carried</h2>
 * Only the hands are checked. A shell packed in a rucksack is padded by everything else in there; one
 * held out in front of you as you land is not. It also keeps the rule legible — a player who loses a
 * stack can see why, and can avoid it by putting them away before the drop.
 *
 * <p>Reads {@link DamageTypeTags#IS_FALL} rather than the fall damage type alone, so a datapack's own
 * falling damage counts and so does the vanilla one. Runs on
 * {@link LivingIncomingDamageEvent} — before mitigation — because what breaks the shell is the
 * <em>landing</em>, not how much of it your boots absorbed.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class OccamyEggshellFragility {

    /** Fall damage above which the shells are at risk at all. */
    public static final float DAMAGE_THRESHOLD = 4.0f;

    /** Chance a qualifying landing destroys a held stack. */
    public static final float BREAK_CHANCE = 0.30f;

    private OccamyEggshellFragility() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.IS_FALL) || event.getAmount() <= DAMAGE_THRESHOLD) {
            return;
        }
        // Both hands roll independently: two stacks are two things to break, and a player holding
        // shells in each hand should not be safer than one holding a single stack.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(ConsumableItemRegistry.OCCAMY_EGGSHELL.get())) {
                continue;
            }
            if (player.getRandom().nextFloat() >= BREAK_CHANCE) {
                continue;
            }
            shatter(player, held);
        }
    }

    /** Takes the stack and says so — quietly, because this is a loss, not an achievement. */
    private static void shatter(ServerPlayer player, ItemStack held) {
        int lost = held.getCount();
        held.setCount(0);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.5f, 1.6f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.25, 0.25, 0.25, 0.01);
        }
        PlayerFeedback.actionBar(player,
                Component.translatable("item.wizards_and_beasts.occamy_eggshell.shattered", lost)
                        .withStyle(ChatFormatting.GRAY));
    }
}
