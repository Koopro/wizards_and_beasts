package at.koopro.neo.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

final class BroomMovement {

    private BroomMovement() {}

    static void tickMovement(BroomEntity b) {
        float speedRatioForTurn = Mth.clamp(Math.abs(b.currentSpeed) / (BroomTuning.MAX_SPEED * BroomTuning.BOOST_MULTIPLIER), 0f, 1f);
        float turnRate = Mth.lerp(speedRatioForTurn, BroomTuning.LOW_SPEED_TURN_RATE, BroomTuning.HIGH_SPEED_TURN_RATE);
        if (b.inputBoosting) {
            turnRate *= 0.82f;
        }
        b.setYRot(Mth.approachDegrees(b.getYRot(), b.inputYaw, turnRate));
        b.setXRot(Mth.approachDegrees(b.getXRot(), Mth.clamp(b.inputPitch, -75f, 75f), BroomTuning.MAX_PITCH_RATE));

        float targetSpeed = 0;
        float accelerationRate = BroomTuning.ACCELERATION;
        if (b.inputForward) {
            targetSpeed = b.inputBoosting ? BroomTuning.MAX_SPEED * BroomTuning.BOOST_MULTIPLIER : BroomTuning.MAX_SPEED;
            accelerationRate = b.inputBoosting ? BroomTuning.BOOST_ACCELERATION : BroomTuning.ACCELERATION;
        } else if (b.inputBackward) {
            targetSpeed = -BroomTuning.MAX_SPEED * 0.22f;
            accelerationRate = BroomTuning.ACCELERATION * 0.85f;
        }

        if (targetSpeed != 0) {
            b.currentSpeed = Mth.lerp(accelerationRate, b.currentSpeed, targetSpeed);
        } else {
            b.currentSpeed = Mth.lerp(BroomTuning.DECELERATION, b.currentSpeed, 0);
            if (Math.abs(b.currentSpeed) < 0.005f) b.currentSpeed = 0;
        }

        float yawRad = b.getYRot() * Mth.DEG_TO_RAD;
        float pitchRad = b.getXRot() * Mth.DEG_TO_RAD;

        double fwdX = -Mth.sin(yawRad) * Mth.cos(pitchRad);
        double fwdY = -Mth.sin(pitchRad);
        double fwdZ = Mth.cos(yawRad) * Mth.cos(pitchRad);

        double targetMotY = fwdY * b.currentSpeed * BroomTuning.PITCH_LIFT_FACTOR;
        if (b.inputUp) targetMotY += BroomTuning.VERTICAL_SPEED;
        if (b.inputDown) targetMotY -= BroomTuning.VERTICAL_SPEED;
        if (!b.inputUp && !b.inputDown && Math.abs(b.currentSpeed) < 0.2f) targetMotY -= BroomTuning.WEAK_GRAVITY;
        b.verticalVelocity = Mth.lerp(BroomTuning.VERTICAL_RESPONSE, b.verticalVelocity, (float) targetMotY);

        float preMoveSpeed = b.currentSpeed;
        Vec3 prevMotion = b.getDeltaMovement();
        float drag = (b.inputForward || b.inputBackward || b.inputUp || b.inputDown) ? BroomTuning.INPUT_DRAG : BroomTuning.COAST_DRAG;
        double motX = Mth.lerp(0.22, prevMotion.x * drag, fwdX * b.currentSpeed);
        double motZ = Mth.lerp(0.22, prevMotion.z * drag, fwdZ * b.currentSpeed);
        b.setDeltaMovement(motX, b.verticalVelocity, motZ);
        b.move(MoverType.SELF, b.getDeltaMovement());

        if (!b.level().isClientSide() && (b.horizontalCollision || b.verticalCollision)) {
            float impactSeverity = BroomImpacts.calculateImpactSeverity(preMoveSpeed, b.horizontalCollision, b.verticalCollision);
            BroomImpacts.handleBlockImpact(b, impactSeverity);
        }
    }

    static void updateTilt(BroomEntity b) {
        b.prevPitchTilt = b.pitchTilt;
        b.prevRollTilt = b.rollTilt;
        b.prevForwardLean = b.forwardLean;

        float accel = b.currentSpeed - b.prevSpeed;
        float targetPitch = Mth.clamp(accel * 200f, -BroomTuning.MAX_PITCH_TILT, BroomTuning.MAX_PITCH_TILT);
        b.pitchTilt = Mth.lerp(BroomTuning.TILT_SMOOTHING, b.pitchTilt, targetPitch);

        float turnRate = Mth.wrapDegrees(b.getYRot() - b.prevSteerYaw);
        float targetRoll = Mth.clamp(-turnRate * 4f, -BroomTuning.MAX_ROLL_TILT, BroomTuning.MAX_ROLL_TILT);
        b.rollTilt = Mth.lerp(BroomTuning.TILT_SMOOTHING, b.rollTilt, targetRoll);

        float speedRatio = Mth.clamp(Math.abs(b.currentSpeed) / BroomTuning.MAX_SPEED, 0f, 1f);
        float targetLean = speedRatio * BroomTuning.MAX_FORWARD_LEAN;
        b.forwardLean = Mth.lerp(BroomTuning.TILT_SMOOTHING, b.forwardLean, targetLean);

        b.prevSpeed = b.currentSpeed;
        b.prevSteerYaw = b.getYRot();
    }
}
