package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.KelpieLureGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Kelpie-style lure/disguise: appears as something harmless and mountable until ridden, then reveals
 * its true form and drags the rider toward water. Generic on config, not Kelpie-specific — any future
 * disguise-based creature opts in via the same ability type. See {@link KelpieLureGoal}.
 */
public record LureDisguise(int revealAfterMountTicks, double dragSpeed, int waterSearchRadius) implements CreatureAbility {

    public static final MapCodec<LureDisguise> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("reveal_after_mount_ticks", 60).forGetter(LureDisguise::revealAfterMountTicks),
            Codec.DOUBLE.optionalFieldOf("drag_speed", 1.3).forGetter(LureDisguise::dragSpeed),
            Codec.INT.optionalFieldOf("water_search_radius", 16).forGetter(LureDisguise::waterSearchRadius)
    ).apply(instance, LureDisguise::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.LURE_DISGUISE;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(1, new KelpieLureGoal(entity, revealAfterMountTicks, dragSpeed, waterSearchRadius));
    }
}
