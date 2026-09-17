package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

/**
 * Keeps away from people — except the ones it has come to accept. A wary creature moves off from any player within
 * {@code radius} unless that player has watched it calmly before and now comes quietly and empty-handed. A
 * purity-sensitive creature (the unicorn) also refuses anyone who has killed one of its kind or whose soul is
 * darkened, and flees those from {@code shun_radius}.
 *
 * <p>This replaces the {@code FEARFUL} trait for creatures that should be approachable by the right person. The rule
 * itself is {@code WildlifeRules.letsNear}.
 */
public record Wary(float radius, float shunRadius, boolean puritySensitive, String slayerFlag)
        implements CreatureAbility {

    public static final MapCodec<Wary> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("radius", 10.0f).forGetter(Wary::radius),
            Codec.FLOAT.optionalFieldOf("shun_radius", 24.0f).forGetter(Wary::shunRadius),
            Codec.BOOL.optionalFieldOf("purity_sensitive", false).forGetter(Wary::puritySensitive),
            Codec.STRING.optionalFieldOf("slayer_flag", "").forGetter(Wary::slayerFlag)
    ).apply(instance, Wary::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.WARY;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        if (puritySensitive) {
            goalSelector.addGoal(1, new Avoid(entity, shunRadius,
                    player -> WildlifeWorld.shuns(player, slayerFlag)));
        }
        goalSelector.addGoal(2, new Avoid(entity, radius,
                player -> !WildlifeWorld.letsNear(player, entity.getType(), puritySensitive, slayerFlag)));
    }

    /** Avoidance of players matching {@code avoid}, spectators excepted, gated on the creatures module. */
    private static final class Avoid extends AvoidEntityGoal<Player> {
        Avoid(GenericBeastEntity entity, float distance, java.util.function.Predicate<Player> avoid) {
            super(entity, Player.class, living -> living instanceof Player player && avoid.test(player),
                    distance, 1.0, 1.35, EntitySelector.NO_SPECTATORS);
        }

        @Override
        public boolean canUse() {
            return ModuleManager.isEnabled(Module.CREATURES) && super.canUse();
        }
    }
}
