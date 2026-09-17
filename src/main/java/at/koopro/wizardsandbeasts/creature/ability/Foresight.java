package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Sees the most likely future. The Demiguise "can see the most probable future" and is caught only by doing something
 * unpredictable; here a player heading straight at it, within {@code range}, is foreseen and the creature steps quietly
 * aside out of their path. A player who stops, circles or comes at it sideways is not foreseen — and can reach it.
 */
public record Foresight(double range, int cooldownTicks, double sidestep) implements CreatureAbility {

    private static final String COOLDOWN = "foresight";

    public static final MapCodec<Foresight> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 7.0).forGetter(Foresight::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 30).forGetter(Foresight::cooldownTicks),
            Codec.DOUBLE.optionalFieldOf("sidestep", 4.0).forGetter(Foresight::sidestep)
    ).apply(instance, Foresight::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.FORESIGHT;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getCooldown(COOLDOWN) > 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(entity) > range * range) {
                continue;
            }
            Vec3 toCreature = entity.position().subtract(player.position());
            if (WildlifeRules.foresees(player.getKnownMovement(), toCreature)) {
                stepAside(entity, level, toCreature, (player.getId() + entity.tickCount) % 2 == 0 ? 1 : -1);
                entity.setCooldown(COOLDOWN, cooldownTicks);
                return;
            }
        }
    }

    /** Moves out of the foreseen path, trying the other side if the first is blocked. */
    public boolean stepAside(GenericBeastEntity entity, ServerLevel level, Vec3 toCreature, int side) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Vec3 offset = WildlifeRules.sidestep(toCreature, sidestep, attempt == 0 ? side : -side);
            if (entity.randomTeleport(entity.getX() + offset.x, entity.getY(), entity.getZ() + offset.z, false)) {
                level.sendParticles(ParticleTypes.WHITE_ASH, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        6, 0.2, 0.3, 0.2, 0.0);
                level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
                        0.25f, 1.9f);
                return true;
            }
        }
        return false;
    }
}
