package at.koopro.wizardsandbeasts.timeturner;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.timeturner.TimeTurnerRules.Moment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Remembers where a Time-Turner's wearer stands, and takes them back — see {@link TimeTurnerRules}.
 *
 * <p>Server-authoritative: the trail lives on the player's {@code TIME_TURNER_TRAIL} attachment and the only thing a
 * client does is hold the use key.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class TimeTurnerService {

    /** Blocks within which a mob hunting the wearer makes the Time-Turner refuse. */
    public static final double DANGER_RADIUS = 8.0;

    private TimeTurnerService() {}

    /** The outcome of one trip, for the item and for tests. */
    public enum Result { BACK, NOT_WOUND, NOT_LONG_ENOUGH, OTHER_WORLD, OCCUPIED, IN_DANGER }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % TimeTurnerRules.RECORD_EVERY_TICKS != 0) {
            return;
        }
        record(player);
    }

    /** Remembers where the wearer stands now, or forgets everything if they are not carrying a Time-Turner. */
    public static void record(ServerPlayer player) {
        TimeTurnerTrail trail = player.getData(ModAttachments.TIME_TURNER_TRAIL.get());
        if (!ModuleManager.isEnabled(Module.ARTEFACTS) || !carries(player) || player.isSpectator()) {
            if (!trail.isEmpty()) {
                trail.clear();
            }
            return;
        }
        trail.record(momentOf(player));
    }

    private static boolean carries(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(TrinketItemRegistry.TIME_TURNER.get()));
    }

    private static Moment momentOf(ServerPlayer player) {
        return new Moment(player.level().getGameTime(), player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ());
    }

    /**
     * Takes the wearer back {@code turns} hours to where they stood, or says why it cannot. A trip spends the
     * trail: the hours just relived are gone and must be lived again before the Time-Turner reaches that far.
     */
    public static Result turnBack(ServerPlayer player, int turns) {
        if (turns < 1) {
            tell(player, Component.translatable("item.wizards_and_beasts.time_turner.not_wound"), ChatFormatting.GRAY);
            return Result.NOT_WOUND;
        }
        if (inDanger(player)) {
            tell(player, Component.translatable("item.wizards_and_beasts.time_turner.danger"), ChatFormatting.DARK_RED);
            return Result.IN_DANGER;
        }
        ServerLevel level = player.level();
        TimeTurnerTrail trail = player.getData(ModAttachments.TIME_TURNER_TRAIL.get());
        Optional<Moment> found = TimeTurnerRules.momentFor(trail.oldestFirst(), level.getGameTime(), turns);
        if (found.isEmpty()) {
            tell(player, Component.translatable("item.wizards_and_beasts.time_turner.not_long_enough", turns),
                    ChatFormatting.GRAY);
            return Result.NOT_LONG_ENOUGH;
        }
        Moment moment = found.get();
        if (!moment.dimension().equals(level.dimension().identifier().toString())) {
            tell(player, Component.translatable("item.wizards_and_beasts.time_turner.other_world"), ChatFormatting.GRAY);
            return Result.OTHER_WORLD;
        }
        Vec3 from = player.position();
        Vec3 to = new Vec3(moment.x(), moment.y(), moment.z());
        if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(from)))) {
            // The world kept what happened since: if something now stands where the wearer stood, that hour is shut.
            tell(player, Component.translatable("item.wizards_and_beasts.time_turner.occupied"), ChatFormatting.GRAY);
            return Result.OCCUPIED;
        }

        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 36, 0.4, 0.6, 0.4, 0.05);
        player.stopRiding();
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        trail.clear();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1.0, to.z, 36, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
        tell(player, Component.translatable("item.wizards_and_beasts.time_turner.back", turns), ChatFormatting.GOLD);
        return Result.BACK;
    }

    /** A mob already hunting the wearer: the Time-Turner is not an escape hatch. */
    private static boolean inDanger(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DANGER_RADIUS),
                mob -> mob.isAlive() && mob.getTarget() == player).isEmpty();
    }

    private static void tell(ServerPlayer player, Component message, ChatFormatting colour) {
        PlayerFeedback.actionBar(player, message.copy().withStyle(colour));
    }
}
