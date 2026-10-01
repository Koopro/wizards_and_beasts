package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.particle.MagicSmoke;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NonNull;

/**
 * Exists only under the full moon. A werewolf creature is a person who has changed for the night; when the moon sets
 * the wolf is gone, and what walks off into the trees is a man or woman nobody would think to follow. It does not drop
 * anything and it does not die — it leaves.
 */
public record MoonBound() implements CreatureAbility {

    private static final int CHECK_TICKS = 40;

    public static final MapCodec<MoonBound> CODEC = MapCodec.unit(MoonBound::new);

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.MOON_BOUND;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % CHECK_TICKS != 0) {
            return;
        }
        if (!WerewolfRules.fullMoonNight(level)) {
            leave(entity, level);
        }
    }

    /** The change reversing: a hiss, a shudder of smoke, and nobody there. */
    public static void leave(GenericBeastEntity entity, ServerLevel level) {
        // Lycanthropy is a curse, so its smoke is dark magic's colour (MagicColours), in the mod's
        // own pixel puffs rather than vanilla's large smoke.
        MagicSmoke.burst(level, entity.getX(), entity.getY() + 1.0, entity.getZ(), MagicColours.DARK_MAGIC,
                10, 0.4, 0.6);
        level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 0.6f);
        entity.discard();
    }
}
