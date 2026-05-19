package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

final class BroomMovement {

    private BroomMovement() {}

    static void tickMovement(BroomEntity b) {
        BroomDefinition def = b.resolveDefinition();
        boolean canBoost = b.getBoostCooldownTicks() <= 0 && b.getBoostTicksRemaining() > 0;
        boolean boostingNow = b.inputBoosting && canBoost;

        float speedRatioForTurn = Mth.clamp(Math.abs(b.currentSpeed) / (def.maxSpeed() * def.boostMultiplier()), 0f, 1f);
        float baseTurnRate = Mth.lerp(speedRatioForTurn, BroomTuning.LOW_SPEED_TURN_RATE, BroomTuning.HIGH_SPEED_TURN_RATE);
        float turnRate = baseTurnRate * def.turnSpeed();
        if (boostingNow) {
            turnRate *= 0.82f;
        }
        b.setYRot(Mth.approachDegrees(b.getYRot(), b.inputYaw, turnRate));
        b.setXRot(Mth.approachDegrees(b.getXRot(), Mth.clamp(b.inputPitch, -75f, 75f), BroomTuning.MAX_PITCH_RATE));

        float targetSpeed = 0;
        float accelerationRate = def.acceleration();
        float skillBonus = 0.0f;
        if (b.getControllingPassenger() instanceof ServerPlayer player) {
            skillBonus = PlayerSkillBonusData.forPlayer(player).broomSpeedBonus();
        }
        float maxForwardSpeed = def.maxSpeed() + skillBonus;
        if (b.inputForward) {
            targetSpeed = boostingNow ? maxForwardSpeed * def.boostMultiplier() : maxForwardSpeed;
            accelerationRate = boostingNow ? def.acceleration() : def.acceleration();
        } else if (b.inputBackward) {
            targetSpeed = -maxForwardSpeed * 0.22f;
            accelerationRate = def.acceleration() * 0.85f;
        }

        if (targetSpeed != 0) {
            b.currentSpeed = Mth.lerp(accelerationRate, b.currentSpeed, targetSpeed);
        } else {
            b.currentSpeed = Mth.lerp(def.deceleration(), b.currentSpeed, 0);
            if (Math.abs(b.currentSpeed) < 0.005f) b.currentSpeed = 0;
        }

        float yawRad = b.getYRot() * Mth.DEG_TO_RAD;
        float pitchRad = b.getXRot() * Mth.DEG_TO_RAD;

        double fwdX = -Mth.sin(yawRad) * Mth.cos(pitchRad);
        double fwdY = -Mth.sin(pitchRad);
        double fwdZ = Mth.cos(yawRad) * Mth.cos(pitchRad);

        double targetMotY = fwdY * b.currentSpeed * BroomTuning.PITCH_LIFT_FACTOR;
        if (b.inputUp) targetMotY += def.ascentSpeed();
        if (b.inputDown) targetMotY -= def.descentSpeed();
        b.verticalVelocity = Mth.lerp(BroomTuning.VERTICAL_RESPONSE, b.verticalVelocity, (float) targetMotY);

        float preMoveSpeed = b.currentSpeed;
        Vec3 prevMotion = b.getDeltaMovement();
        float drag = (b.inputForward || b.inputBackward || b.inputUp || b.inputDown) ? BroomTuning.INPUT_DRAG : BroomTuning.COAST_DRAG;
        double motX = Mth.lerp(def.lerpFactor(), prevMotion.x * drag, fwdX * b.currentSpeed);
        double motZ = Mth.lerp(def.lerpFactor(), prevMotion.z * drag, fwdZ * b.currentSpeed);
        b.setDeltaMovement(motX, b.verticalVelocity, motZ);
        b.move(MoverType.SELF, b.getDeltaMovement());

        if (!b.level().isClientSide() && (b.horizontalCollision || b.verticalCollision)) {
            float impactSeverity = BroomImpacts.calculateImpactSeverity(preMoveSpeed, b.horizontalCollision, b.verticalCollision);
            BroomImpacts.handleBlockImpact(b, impactSeverity);
        }

        if (boostingNow) {
            b.setBoostTicksRemaining(b.getBoostTicksRemaining() - 1);
            if (b.getBoostTicksRemaining() <= 0) {
                b.setBoostCooldownTicks(def.boostCooldownTicks());
            }
        } else {
            if (b.getBoostCooldownTicks() > 0) {
                b.setBoostCooldownTicks(b.getBoostCooldownTicks() - 1);
                if (b.getBoostCooldownTicks() == 0) {
                    b.setBoostTicksRemaining(def.boostDurationTicks());
                }
            }
        }
    }

    static void updateTilt(BroomEntity b) {
        b.prevPitchTilt = b.pitchTilt;
        b.prevRollTilt = b.rollTilt;
        b.prevForwardLean = b.forwardLean;

        float accel = b.currentSpeed - b.prevSpeed;
        // Positive XRot = looking down → subtract to push pitchTilt negative (nose-down)
        float targetPitch = Mth.clamp(accel * 200f - b.getXRot() * 0.4f, -BroomTuning.MAX_PITCH_TILT, BroomTuning.MAX_PITCH_TILT);
        b.pitchTilt = Mth.lerp(BroomTuning.TILT_SMOOTHING, b.pitchTilt, targetPitch);

        float turnRate = Mth.wrapDegrees(b.getYRot() - b.prevSteerYaw);
        BroomDefinition def = b.resolveDefinition();
        float maxLeanAngle = 30.0f * def.handlingRating();
        float targetRoll = Mth.clamp(-turnRate * 4f, -maxLeanAngle, maxLeanAngle);
        float stabilityLerp = 0.18f + (def.stabilityRating() * 0.22f);
        b.rollTilt = Mth.lerp(stabilityLerp, b.rollTilt, targetRoll);

        float speedRatio = Mth.clamp(Math.abs(b.currentSpeed) / def.maxSpeed(), 0f, 1f);
        // Negative = nose-down tuck at speed; fades when pitching steeply so it doesn't fight entity pitch rotation
        float pitchCos = Math.abs(Mth.cos(b.getXRot() * Mth.DEG_TO_RAD));
        float targetLean = -speedRatio * maxLeanAngle * pitchCos;
        b.forwardLean = Mth.lerp(stabilityLerp, b.forwardLean, targetLean);

        b.prevSpeed = b.currentSpeed;
        b.prevSteerYaw = b.getYRot();
    }
}
