package at.koopro.wizardsandbeasts.item.hallow;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The shades the Resurrection Stone calls, and the one thing they do.
 *
 * <p><i>Deathly Hallows</i> ch. 34: Harry turns the Stone thrice and his dead walk beside him into the Forest, and
 * "the dementors' chill did not overcome him; he passed through it with his companions". That is this: while the
 * shades walk with a player, no Dementor can feel them — no chill, no pursuit, no Kiss. They are company, not a
 * weapon. Unlike a Patronus they drive nothing off, and they protect no one else.
 *
 * <p>They stay while the Stone does. Harry let it fall before he faced Voldemort and they were gone; a player who
 * puts the Stone down, or drops it, sends them back too.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ShadesOfTheDead {

    private ShadesOfTheDead() {}

    /** Whether the shades walk with this player now. */
    public static boolean walkWith(Player player) {
        return player.getData(ModAttachments.SHADES_UNTIL.get()) > player.level().getGameTime();
    }

    /** Calls the shades to walk with {@code player} for {@code ticks}. */
    public static void call(ServerPlayer player, int ticks) {
        player.setData(ModAttachments.SHADES_UNTIL.get(), player.level().getGameTime() + ticks);
    }

    /** Sends them back. */
    public static void dismiss(ServerPlayer player) {
        player.setData(ModAttachments.SHADES_UNTIL.get(), 0L);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            update(player);
        }
    }

    /** Once a second: the shades fade when their time is up, or leave when the Stone does. */
    public static void update(ServerPlayer player) {
        long until = player.getData(ModAttachments.SHADES_UNTIL.get());
        if (until == 0L) {
            return;
        }
        if (!walkWith(player)) {
            dismiss(player);
            player.displayClientMessage(Component.translatable("item.wizards_and_beasts.resurrection_stone.fade"), true);
            return;
        }
        if (!player.getInventory().contains(stack -> stack.is(DarkArtefactItemRegistry.RESURRECTION_STONE.get()))) {
            dismiss(player);
            player.displayClientMessage(Component.translatable("item.wizards_and_beasts.resurrection_stone.let_go"), true);
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.8, 0.6, 0.8, 0.005);
        }
    }
}
