package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.LycanthropyInfection;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * The bite of a werewolf in wolf form carries the curse. Whether it takes is {@link LycanthropyInfection}'s decision:
 * full moon, a human victim, and a server that has not turned infection off.
 */
public record LycanthropicBite() implements CreatureAbility {

    public static final MapCodec<LycanthropicBite> CODEC = MapCodec.unit(LycanthropicBite::new);

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.LYCANTHROPIC_BITE;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        if (target instanceof ServerPlayer victim && victim.isAlive()) {
            // onMeleeContact runs only after a hit that landed, so blood was drawn.
            LycanthropyInfection.bitten(victim, 1.0f);
        }
    }
}
