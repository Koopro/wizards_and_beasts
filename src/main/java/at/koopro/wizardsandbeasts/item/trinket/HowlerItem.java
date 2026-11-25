package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A Howler: a red letter that screams its message at the recipient and everyone near them, then burns.
 *
 * <ul>
 *   <li><b>Blank</b> — use it to write the message ({@code HowlerWriteScreen}); it is then sealed with
 *       the writer's name.</li>
 *   <li><b>Sealed</b> — sent like any parcel, by owl ({@code /post send <player>} with it in hand).</li>
 *   <li><b>Opened</b> — use it: the message is shouted, in capitals, to everyone within
 *       {@link #SHOUT_RADIUS} blocks, and the letter goes up in flames.</li>
 *   <li><b>Ignored</b> — a sealed Howler in anyone's pocket but its writer's opens itself after
 *       {@link #FUSE_TICKS}, worse for the wait: it bangs as it bursts (Ron: "I got one from my gran
 *       once, and I ignored it… it was horrible").</li>
 * </ul>
 */
@NullMarked
public class HowlerItem extends Item implements AnimatedItem {

    /** How far the shout carries: the whole Great Hall heard Mrs Weasley. */
    public static final double SHOUT_RADIUS = 30.0;
    /** One minute in the recipient's pocket before it opens by itself. */
    public static final long FUSE_TICKS = 1200L;
    /** Longest message a Howler holds; also the server-side cap on what a client may seal. */
    public static final int MAX_MESSAGE = 120;

    public HowlerItem(Properties properties) {
        super(properties);
    }

    public static boolean isSealed(ItemStack stack) {
        return stack.has(ModDataComponents.HOWLER_MESSAGE.get());
    }

    /** Seals a blank Howler with a message. Returns false if the stack was not a blank Howler. */
    public static boolean seal(ItemStack stack, String author, String message) {
        if (!(stack.getItem() instanceof HowlerItem) || isSealed(stack)) {
            return false;
        }
        String text = message.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (text.length() > MAX_MESSAGE) {
            text = text.substring(0, MAX_MESSAGE);
        }
        stack.set(ModDataComponents.HOWLER_MESSAGE.get(), text);
        stack.set(ModDataComponents.HOWLER_AUTHOR.get(), author);
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isSealed(stack)) {
            if (level.isClientSide()) {
                ClientScreenHooksInvoker.invoke("openHowlerWriteScreen");
            }
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server) {
            burst(server, player, stack, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!isSealed(stack) || !(entity instanceof ServerPlayer holder)) {
            return;
        }
        String author = stack.getOrDefault(ModDataComponents.HOWLER_AUTHOR.get(), "");
        if (author.equals(holder.getGameProfile().name())) {
            return;
        }
        long now = level.getGameTime();
        Long armed = stack.get(ModDataComponents.HOWLER_ARMED_AT.get());
        if (armed == null) {
            stack.set(ModDataComponents.HOWLER_ARMED_AT.get(), now);
            holder.displayClientMessage(Component.translatable("item.wizards_and_beasts.howler.smoking")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (now - armed >= FUSE_TICKS) {
            burst(level, holder, stack, true);
        } else if ((now - armed) % 40 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, holder.getX(), holder.getY() + 1.0, holder.getZ(),
                    3, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /**
     * The Howler goes off: the message, in capitals, to everyone in earshot; then it burns.
     *
     * @param ignored whether it opened itself because nobody would
     */
    public static void burst(ServerLevel level, Player holder, ItemStack stack, boolean ignored) {
        String message = stack.getOrDefault(ModDataComponents.HOWLER_MESSAGE.get(), "");
        String author = stack.getOrDefault(ModDataComponents.HOWLER_AUTHOR.get(), "?");
        Component shout = Component.translatable("item.wizards_and_beasts.howler.shout", author,
                        message.toUpperCase(Locale.ROOT))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer listener : level.getPlayers(p -> p.distanceToSqr(holder) <= SHOUT_RADIUS * SHOUT_RADIUS)) {
            listener.sendSystemMessage(shout);
        }
        double x = holder.getX();
        double y = holder.getEyeY() + 0.3;
        double z = holder.getZ();
        level.playSound(null, x, y, z, at.koopro.wizardsandbeasts.registry.ModSounds.HOWLER_SHOUT.get(),
                SoundSource.PLAYERS, 2.0f, 1.0f);
        level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 24, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 30, 0.6, 0.6, 0.6, 0.01);
        if (ignored) {
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
            level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
            holder.hurtServer(level, level.damageSources().onFire(), 2.0f);
        }
        holder.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        stack.shrink(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        if (isSealed(stack)) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.howler.from",
                    stack.getOrDefault(ModDataComponents.HOWLER_AUTHOR.get(), "?")).withStyle(ChatFormatting.RED));
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.howler.sealed_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.howler.blank_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
