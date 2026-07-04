package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Common ability: an oppressive presence. Once per second, applies a configurable bundle of debuffs to
 * every nearby player (despair/fear/melancholy). The Obscurus/Glumbumble/Erkling/Sphinx kit; the Boggart
 * has its own stronger reveal-on-look variant.
 */
public record DreadAura(double radius, @NonNull List<AbilitySupport.EffectSpec> effects) implements CreatureAbility {

    public static final MapCodec<DreadAura> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 6.0).forGetter(DreadAura::radius),
            AbilitySupport.EffectSpec.LIST_CODEC.fieldOf("effects").forGetter(DreadAura::effects)
    ).apply(instance, DreadAura::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.DREAD_AURA;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 20 != 0) {
            return;
        }
        List<Player> players = AbilitySupport.nearbyPlayers(entity, radius);
        if (players.isEmpty()) {
            return;
        }
        for (Player player : players) {
            AbilitySupport.applyAll(player, effects);
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.WITCH, 4);
    }
}
