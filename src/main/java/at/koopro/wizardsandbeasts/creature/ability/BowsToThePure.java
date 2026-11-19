package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

/**
 * The Qilin bows before the pure of heart (<i>The Secrets of Dumbledore</i>: it bows to Dumbledore, and to the
 * one fit to lead). A player within {@code range} whom the purity rules do not shun is bowed to once per
 * {@code cooldown_ticks}: a light at their feet and a little luck. One it shuns is not bowed to.
 */
public record BowsToThePure(double range, int cooldownTicks) implements CreatureAbility {

    public static final MapCodec<BowsToThePure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 6.0).forGetter(BowsToThePure::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 24000).forGetter(BowsToThePure::cooldownTicks)
    ).apply(instance, BowsToThePure::new));

    private static final String LAST_BOW = "wizards_and_beasts.qilin_last_bow";

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.BOWS_TO_THE_PURE;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !AiCadence.every(entity, 40)) {
            return;
        }
        for (Player player : AbilitySupport.nearbyPlayers(entity, range)) {
            if (WildlifeWorld.shuns(player, "")) {
                continue;
            }
            long now = level.getGameTime();
            long last = player.getPersistentData().getLongOr(LAST_BOW, Long.MIN_VALUE / 2);
            if (now - last < cooldownTicks) {
                continue;
            }
            player.getPersistentData().putLong(LAST_BOW, now);
            entity.getLookControl().setLookAt(player);
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, 0));
            level.sendParticles(at.koopro.wizardsandbeasts.particle.MagicMotes.gold(), player.getX(), player.getY() + 0.1, player.getZ(),
                    16, 0.4, 0.05, 0.4, 0.01);
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL,
                    1.0f, 1.2f);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "creature.wizards_and_beasts.qilin.bows").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return;
        }
    }
}
