package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.entity.broom.handling.HandlingProfileRegistry;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
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

    /** This broom's own boosted ceiling — the only honest denominator for a severity fraction. */
    static float topSpeedOf(BroomEntity b) {
        BroomDefinition def = b.resolveDefinition();
        return def.maxSpeed() * def.boostMultiplier();
    }

    /**
     * Entry point from {@link BroomMovement} for a tick that collided with the world.
     *
     * <p>Takes the descent rate as well as the forward speed. The old signature had only the
     * forward scalar and used it for both axes, so how hard you hit the ground had nothing to do
     * with how fast you were falling — a vertical dive at terminal speed with no forward motion
     * registered as a severity of exactly zero.
     */
    static void handleCollision(BroomEntity b, float forwardSpeed, float descentSpeed) {
        float topSpeed = topSpeedOf(b);
        // Setting a broom down is not crashing it. Without this every flight ends in a durability
        // hit, because every flight ends in a vertical collision.
        if (at.koopro.wizardsandbeasts.Config.broomGentleLanding
                && b.verticalCollision && !b.horizontalCollision
                && BroomFlightRules.isGentleLanding(forwardSpeed, descentSpeed, topSpeed)) {
            b.onGentleLanding();
            return;
        }
        float severity = BroomFlightRules.impactSeverity(
                forwardSpeed, descentSpeed, topSpeed, b.horizontalCollision, b.verticalCollision);
        handleBlockImpact(b, severity);
    }

    static void handleBlockImpact(BroomEntity b, float impactSeverity) {
        LivingEntity rider = b.getControllingPassenger();
        if (impactSeverity >= BroomTuning.SEVERE_IMPACT_THRESHOLD) {
            handleCrash(b, impactSeverity);
            return;
        }

        if (impactSeverity >= BroomTuning.MODERATE_IMPACT_THRESHOLD) {
            b.applyDurabilityDamage(b.resolveDefinition().handling().moderateImpactDurabilityLoss());
            b.currentSpeed *= BroomTuning.MODERATE_SPEED_DAMPING;
            b.setDeltaMovement(b.getDeltaMovement().scale(0.55).add(0, BroomTuning.MODERATE_BUMP_Y, 0));
            if (rider != null) {
                rider.setDeltaMovement(rider.getDeltaMovement().add(0, BroomTuning.MODERATE_BUMP_Y, 0));
                rider.hurtMarked = true;
            }
            return;
        }

        if (impactSeverity >= BroomTuning.MINOR_IMPACT_THRESHOLD) {
            BroomDefinition minorDef = b.resolveDefinition();
            b.applyDurabilityDamage(HandlingProfileRegistry.of(minorDef).minorImpactDurabilityLoss(
                    minorDef.handling().minorImpactDurabilityLoss(), minorDef));
            announceDamage(b, false);
            if (b.level() instanceof ServerLevel minorLevel) {
                minorLevel.playSound(null, b.blockPosition(), ModSounds.BROOM_CRASH_MINOR.get(),
                        SoundSource.PLAYERS, 0.5f, 1.0f + b.getRandom().nextFloat() * 0.2f);
            }
            b.currentSpeed *= BroomTuning.MINOR_SPEED_DAMPING;
            b.setDeltaMovement(b.getDeltaMovement().scale(0.78).add(0, BroomTuning.MINOR_BUMP_Y, 0));
        }
    }

    /**
     * Tells the rider what just broke, on the action bar.
     *
     * <p>The action bar rather than a toast: this is an in-the-moment transient during play, it fires
     * often, and a toast for every fence post clipped would bury the notices that matter. It also has
     * to be readable while the player is still flying, which chat is not.
     *
     * <p>Only the rider is told. An onlooker sees the twigs and hears the crack, which is the right
     * amount of information about somebody else's broom.
     */
    private static void announceDamage(BroomEntity b, boolean severe) {
        if (!(b.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player rider)) {
            return;
        }
        PlayerFeedback.actionBar(rider, Component.translatable(severe
                ? "broom.wizards_and_beasts.damage.severe"
                : "broom.wizards_and_beasts.damage.minor"));
    }

    /**
     * A burst of broken twigs at the bristles, plus dust where the broom actually struck.
     *
     * <p>Seeded at {@code tailPosition()} rather than at the entity centre, so the debris comes off
     * the part of the broom made of twigs.
     */
    private static void spawnTwigBurst(ServerLevel level, BroomEntity b) {
        Vec3 tail = b.tailPosition();
        level.sendParticles(ModParticles.BROOM_TRAIL_DUST.get(),
                tail.x, tail.y, tail.z, 14, 0.22, 0.22, 0.22, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                b.getX(), b.getY() + 0.3, b.getZ(), 8, 0.2, 0.2, 0.2, 0.12);
    }

    static void handleCrash(BroomEntity b, float impactSeverity) {
        if (!(b.level() instanceof ServerLevel serverLevel)) return;
        BroomDefinition def = b.resolveDefinition();
        // Wear scales with how punishing this broom is to crash, not only with its base loss: a
        // Firebolt's 1.35 multiplier is the whole reason it is frightening to fly at a wall, and
        // applying it to the rider but not to the broom made the broom the safe half of the pair.
        float crashMultiplier = HandlingProfileRegistry.of(def).crashDamageMultiplier(def);
        b.applyDurabilityDamage(Math.max(1,
                Math.round(def.handling().severeImpactDurabilityLoss() * crashMultiplier)));
        announceDamage(b, true);
        spawnTwigBurst(serverLevel, b);
        serverLevel.playSound(null, b.blockPosition(), ModSounds.BROOM_CRASH_SEVERE.get(),
                SoundSource.PLAYERS, 0.9f, 0.9f + b.getRandom().nextFloat() * 0.2f);

        LivingEntity rider = b.getControllingPassenger();

        float severityRatio = Mth.clamp(impactSeverity / BroomTuning.SEVERE_IMPACT_THRESHOLD, 0f, 1.4f);
        // crashDamageMultiplier is applied before the cap, not after, so a broom that softens its
        // crashes actually softens them rather than merely taking longer to reach the same ceiling.
        float damage = Math.min(BroomTuning.MAX_CRASH_DAMAGE,
                severityRatio * BroomTuning.MAX_CRASH_DAMAGE * crashMultiplier);

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
                float impactScale = Mth.clamp(Math.abs(b.currentSpeed) / topSpeedOf(b), 0f, 1f);
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
        float impactScale = Mth.clamp(
                relSpeed / Math.max(topSpeedOf(self), topSpeedOf(other)), 0.2f, 1.0f);

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
