package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The Elixir of Life, drawn from the Philosopher's Stone: "a substance … which will make the drinker immortal", so
 * long as it is drunk regularly (<i>Philosopher's Stone</i> ch. 13) — Flamel kept himself alive for six centuries.
 *
 * <p>In play: one draught per in-game day. For that day the drinker's next death does not happen — they are left
 * standing on their last heartbeats, and the draught is spent. Drinking again once the day is out renews it. That is
 * the one thing the Elixir does: it keeps you alive. It is not a healing potion, a shield or a cleanse; the Stone used
 * to be a permanent Regeneration II / Absorption III / Resistance aura that wiped every effect
 * (documentation/CANON_AUDIT.md C-9). A Dementor's Kiss is not death, and the Elixir does nothing about it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ElixirOfLife {

    /** One draught lasts, and may be renewed after, an in-game day. */
    public static final long DAY_TICKS = 24000L;
    /** Health the Elixir leaves someone it has just kept alive. */
    public static final float SPARED_HEALTH = 2.0f;

    private ElixirOfLife() {}

    /** Whether this player may drink now. */
    public static boolean canDrink(ServerPlayer player) {
        return player.level().getGameTime() >= player.getData(ModAttachments.ELIXIR_NEXT_DRAUGHT.get());
    }

    /** Whether the Elixir currently holds death off this player. */
    public static boolean sustains(ServerPlayer player) {
        return player.level().getGameTime() < player.getData(ModAttachments.ELIXIR_UNTIL.get());
    }

    /** Drinks a draught: a day of protection, and the next draught a day away. */
    public static void drink(ServerPlayer player) {
        long now = player.level().getGameTime();
        player.setData(ModAttachments.ELIXIR_UNTIL.get(), now + DAY_TICKS);
        player.setData(ModAttachments.ELIXIR_NEXT_DRAUGHT.get(), now + DAY_TICKS);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !sustains(player)) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(SPARED_HEALTH);
        player.clearFire();
        player.setData(ModAttachments.ELIXIR_UNTIL.get(), 0L);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
                    30, 0.4, 0.6, 0.4, 0.2);
            level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
                    1.0f, 0.6f);
        }
        player.displayClientMessage(Component.translatable("item.wizards_and_beasts.philosophers_stone.spared")
                .withStyle(ChatFormatting.GOLD), true);
    }
}
