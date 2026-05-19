package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class BroomImpacts {

    private BroomImpacts() {}

    static float calculateImpactSeverity(float impactSpeed, boolean hitHorizontal, boolean hitVertical) {
        float speedRatio = Math.abs(impactSpeed) / (BroomTuning.MAX_SPEED * BroomTuning.BOOST_MULTIPLIER);
        float axisWeight = hitHorizontal ? 1.0f : BroomTuning.VERTICAL_IMPACT_WEIGHT;
        if (hitHorizontal && hitVertical) {
            axisWeight = 1.1f;
        }
        return speedRatio * axisWeight;
    }

    static void handleBlockImpact(BroomEntity b, float impactSeverity) {
        LivingEntity rider = b.getControllingPassenger();
        if (impactSeverity >= BroomTuning.SEVERE_IMPACT_THRESHOLD) {
            handleCrash(b, impactSeverity);
            return;
        }

        if (impactSeverity >= BroomTuning.MODERATE_IMPACT_THRESHOLD) {
            b.applyDurabilityDamage(2);
            b.currentSpeed *= BroomTuning.MODERATE_SPEED_DAMPING;
            b.setDeltaMovement(b.getDeltaMovement().scale(0.55).add(0, BroomTuning.MODERATE_BUMP_Y, 0));
            if (rider != null) {
                rider.setDeltaMovement(rider.getDeltaMovement().add(0, BroomTuning.MODERATE_BUMP_Y, 0));
                rider.hurtMarked = true;
            }
            return;
        }

        if (impactSeverity >= BroomTuning.MINOR_IMPACT_THRESHOLD) {
            b.applyDurabilityDamage(1);
            b.currentSpeed *= BroomTuning.MINOR_SPEED_DAMPING;
            b.setDeltaMovement(b.getDeltaMovement().scale(0.78).add(0, BroomTuning.MINOR_BUMP_Y, 0));
        }
    }

    static void handleCrash(BroomEntity b, float impactSeverity) {
        if (!(b.level() instanceof ServerLevel serverLevel)) return;
        b.applyDurabilityDamage(3);

        LivingEntity rider = b.getControllingPassenger();

        float severityRatio = Mth.clamp(impactSeverity / BroomTuning.SEVERE_IMPACT_THRESHOLD, 0f, 1.4f);
        float damage = Math.min(BroomTuning.MAX_CRASH_DAMAGE, severityRatio * BroomTuning.MAX_CRASH_DAMAGE);

        if (rider != null) {
            rider.stopRiding();
            rider.hurtServer(serverLevel, b.damageSources().flyIntoWall(), damage);

            float yawRad = b.getYRot() * Mth.DEG_TO_RAD;
            double knockX = Mth.sin(yawRad) * BroomTuning.CRASH_KNOCKBACK;
            double knockZ = -Mth.cos(yawRad) * BroomTuning.CRASH_KNOCKBACK;
            rider.setDeltaMovement(knockX, BroomTuning.CRASH_KNOCKBACK * 0.5, knockZ);
            rider.hurtMarked = true;
        }

        serverLevel.sendParticles(ParticleTypes.CLOUD,
                b.getX(), b.getY() + 0.5, b.getZ(),
                12, 0.5, 0.3, 0.5, 0.05);
        serverLevel.sendParticles(ParticleTypes.POOF,
                b.getX(), b.getY() + 0.3, b.getZ(),
                6, 0.3, 0.2, 0.3, 0.02);

        b.level().playSound(null, b.getX(), b.getY(), b.getZ(),
                ModSounds.BROOM_CRASH.get(), SoundSource.PLAYERS,
                0.6f + Math.min(severityRatio, 1f) * 0.4f, 0.8f + b.level().random.nextFloat() * 0.4f);

        b.dropBroomItem();
        b.discard();
    }

    static void scanEntityCollisions(BroomEntity b, LivingEntity rider) {
        if (Math.abs(b.currentSpeed) < BroomTuning.COLLISION_SPEED_MIN) return;

        List<Entity> nearby = b.level().getEntities(b, b.getBoundingBox().inflate(0.3),
                e -> e != rider && e.isAlive() && !e.isPassenger());

        for (Entity target : nearby) {
            int targetId = target.getId();
            if (b.collisionCooldowns.containsKey(targetId)) continue;

            b.collisionCooldowns.put(targetId, BroomTuning.COLLISION_COOLDOWN_TICKS);

            float yawRad = b.getYRot() * Mth.DEG_TO_RAD;
            double dirX = -Mth.sin(yawRad);
            double dirZ = Mth.cos(yawRad);

            if (target instanceof BroomEntity otherBroom) {
                handleBroomCollision(b, otherBroom, dirX, dirZ);
            } else if (target instanceof LivingEntity living
                    && b.level() instanceof ServerLevel serverLevel) {
                float impactScale = Mth.clamp(Math.abs(b.currentSpeed) / (BroomTuning.MAX_SPEED * BroomTuning.BOOST_MULTIPLIER), 0f, 1f);
                float force = impactScale * BroomTuning.KNOCKBACK_FORCE;
                living.knockback(force, -dirX, -dirZ);
                living.hurtServer(serverLevel, b.damageSources().mobAttack(rider),
                        BroomTuning.ENTITY_HIT_DAMAGE * impactScale);
                living.hurtMarked = true;
            }
        }
    }

    static void handleBroomCollision(BroomEntity self, BroomEntity other, double dirX, double dirZ) {
        Vec3 selfMotion = self.getDeltaMovement();
        Vec3 otherMotion = other.getDeltaMovement();
        Vec3 relativeMotion = selfMotion.subtract(otherMotion);
        float relSpeed = (float) relativeMotion.horizontalDistance();
        float impactScale = Mth.clamp(relSpeed / (BroomTuning.MAX_SPEED * BroomTuning.BOOST_MULTIPLIER), 0.2f, 1.0f);

        self.currentSpeed *= Mth.lerp(impactScale, 0.72f, 0.52f);
        other.currentSpeed *= Mth.lerp(impactScale, 0.72f, 0.52f);

        double pushStrength = Mth.lerp(impactScale, 0.22, 0.45);
        self.push(dirX * -pushStrength, 0.08 + impactScale * 0.05, dirZ * -pushStrength);
        other.push(dirX * pushStrength, 0.08 + impactScale * 0.05, dirZ * pushStrength);

        self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
                SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS,
                0.75f + impactScale * 0.15f, 0.9f + self.level().random.nextFloat() * 0.25f);
    }

    static void tickCollisionCooldowns(BroomEntity b) {
        if (b.collisionCooldowns.isEmpty()) return;
        b.collisionCooldowns.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
    }
}
