package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Erumpent): the horn injects an explosive fluid, so anything it gores bursts. On melee
 * contact a contained explosion ({@link Level.ExplosionInteraction#NONE} — no terrain damage) detonates on
 * the victim: extra damage + knockback + the explosion flash. Distinct from the Erumpent's own
 * {@code EXPLODE_ON_DEATH} trait (that fires when the beast dies; this fires when it lands a hit).
 */
public record ExplosiveHorn(float power) implements CreatureAbility {

    public static final MapCodec<ExplosiveHorn> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("power", 2.0f).forGetter(ExplosiveHorn::power)
    ).apply(instance, ExplosiveHorn::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.EXPLOSIVE_HORN;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // Contained burst on the gored target: hurts entities + knockback, leaves the world intact.
        level.explode(entity, target.getX(), target.getY(), target.getZ(), power, Level.ExplosionInteraction.NONE);
    }
}
