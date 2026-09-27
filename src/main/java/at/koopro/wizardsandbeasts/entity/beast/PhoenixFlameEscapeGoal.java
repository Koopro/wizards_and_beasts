package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * A badly hurt phoenix does not flap away from its attacker: it vanishes in flame and reappears out of reach.
 *
 * <p>Canon: phoenixes "can disappear and reappear at will" (Fantastic Beasts). The gameplay reading is that it
 * does so when cornered — below {@link WildlifeRules#FLAME_ESCAPE_HEALTH_FRACTION} of its health, struck in the
 * last three seconds, attacker within twelve blocks, flame travel off cooldown. It reappears sixteen to twenty-four
 * blocks away on the far side, above the surface, somewhere {@link PhoenixFlameTravel#landingNear} accepts.
 */
final class PhoenixFlameEscapeGoal extends Goal {

    private final PhoenixEntity phoenix;

    PhoenixFlameEscapeGoal(PhoenixEntity phoenix) {
        this.phoenix = phoenix;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = phoenix.getLastHurtByMob();
        return phoenix.canFlameTravel()
                && phoenix.getHealth() < phoenix.getMaxHealth() * WildlifeRules.FLAME_ESCAPE_HEALTH_FRACTION
                && attacker != null && attacker.isAlive()
                && phoenix.tickCount - phoenix.getLastHurtByMobTimestamp() < 60
                && attacker.distanceToSqr(phoenix) < 12.0 * 12.0;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (!(phoenix.level() instanceof ServerLevel level) || phoenix.getLastHurtByMob() == null) {
            return;
        }
        Vec3 away = phoenix.position().subtract(phoenix.getLastHurtByMob().position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize().yRot((phoenix.getRandom().nextFloat() - 0.5f) * 1.2f);
        double distance = 16.0 + phoenix.getRandom().nextDouble() * 8.0;
        Vec3 flat = phoenix.position().add(away.scale(distance));
        BlockPos column = BlockPos.containing(flat);
        if (!level.isLoaded(column)) {
            return;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        Optional<Vec3> landing = PhoenixFlameTravel.landingNear(level, phoenix,
                new Vec3(flat.x, surface + 2, flat.z), 3);
        landing.ifPresent(target -> phoenix.flameTravel(level, target));
    }
}
