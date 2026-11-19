package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * De-gnoming (<i>Chamber of Secrets</i> ch. 3): grab the gnome, swing it round until it is dizzy, and throw it
 * over the hedge. Sneak and use an empty hand on a gnome: it is flung {@code strength} the way you are
 * looking, lands dizzy, and has lost all interest in you and your garden for a while.
 */
public record GnomeToss(float strength, int dizzyTicks) implements CreatureAbility {

    public static final MapCodec<GnomeToss> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("strength", 1.6f).forGetter(GnomeToss::strength),
            Codec.INT.optionalFieldOf("dizzy_ticks", 200).forGetter(GnomeToss::dizzyTicks)
    ).apply(instance, GnomeToss::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.GNOME_TOSS;
    }

    @Override
    public @NonNull InteractionResult onInteract(@NonNull GenericBeastEntity entity, @NonNull Player player,
                                                 @NonNull InteractionHand hand) {
        if (!player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (entity.level() instanceof ServerLevel level) {
            toss(level, entity, player);
        }
        return InteractionResult.SUCCESS;
    }

    public void toss(ServerLevel level, GenericBeastEntity entity, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        entity.setDeltaMovement(flat.x * strength, 0.55 + strength * 0.15, flat.z * strength);
        entity.hurtMarked = true;
        entity.setTarget(null);
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, dizzyTicks, 2));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dizzyTicks, 0));
        level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getEyeY(), entity.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.6f);
    }
}
