package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.item.broom.BroomPolish;
import at.koopro.wizardsandbeasts.entity.broom.handling.BroomHandlingProfile;
import at.koopro.wizardsandbeasts.entity.broom.handling.HandlingMath;
import at.koopro.wizardsandbeasts.entity.broom.handling.HandlingProfileRegistry;
import at.koopro.wizardsandbeasts.broom.SnidgetFeather;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

final class BroomMovement {

    private BroomMovement() {}

    static void tickMovement(BroomEntity b) {
        BroomDefinition def = b.resolveDefinition();
        BroomHandlingProfile handling = HandlingProfileRegistry.of(def);
        boolean boostingNow = b.isBoostFiring();
        // Edge, not level: the hook means "a boost started", and a level test would re-fire it every
        // tick the boost ran.
        if (boostingNow && !b.wasBoostFiring) {
            handling.onBoostStart(b, def);
        }
        b.wasBoostFiring = boostingNow;

        float speedRatioForTurn = Mth.clamp(Math.abs(b.currentSpeed) / (def.maxSpeed() * def.boostMultiplier()), 0f, 1f);
        float baseTurnRate = Mth.lerp(speedRatioForTurn, BroomTuning.LOW_SPEED_TURN_RATE, BroomTuning.HIGH_SPEED_TURN_RATE);
        float turnRate = baseTurnRate * def.turnSpeed();
        if (boostingNow) {
            turnRate *= 0.82f;
        }
        turnRate = handling.modifyTurnRate(turnRate, b, def, boostingNow);
        // Read before the yaw is stepped: afterwards the broom has already closed on the rider's
        // heading and every broom looks like it is holding one.
        boolean steering = b.isSteering();
        // A freshly polished handle runs truer. This is the second half of what a tin buys, and the
        // half a player notices while flying rather than while looking at a durability bar.
        float wander = handling.yawWander(b, def, speedRatioForTurn, boostingNow, steering);
        if (b.isPolished()) {
            wander *= BroomPolish.WOBBLE_RELIEF;
        }
        b.setYRot(Mth.approachDegrees(b.getYRot(), b.inputYaw, turnRate) + wander);
        b.setXRot(Mth.approachDegrees(b.getXRot(),
                Mth.clamp(b.inputPitch, -BroomTuning.MAX_PITCH_DEGREES, BroomTuning.MAX_PITCH_DEGREES),
                BroomTuning.MAX_PITCH_RATE));

        float targetSpeed = 0;
        float accelerationRate = def.acceleration();
        float skillBonus = 0.0f;
        boolean snidget = false;
        if (b.getControllingPassenger() instanceof ServerPlayer player) {
            skillBonus = PlayerSkillBonusData.forPlayer(player).broomSpeedBonus();
            snidget = SnidgetFeather.isHeld(player);
            if (snidget && b.inputForward) {
                // Charged only against powered flight. A broom parked in the air with a feather in
                // the rider's off hand is not borrowing anything from it.
                SnidgetFeather.wear(player, b.tickCount);
            }
        }
        // Config scales the authored value, then the skill bonus is added on top: a flat bonus that
        // was balanced against real block-per-tick speeds should not itself be halved by a server
        // that dialled brooms down.
        float maxForwardSpeed = def.maxSpeed() * at.koopro.wizardsandbeasts.Config.broomSpeedMultiplier + skillBonus;
        // The feather multiplies the finished figure rather than the authored one, so it scales with
        // whatever the server decided a broom is worth instead of around it.
        if (snidget) {
            maxForwardSpeed = SnidgetFeather.applySpeedBonus(maxForwardSpeed);
        }
        if (b.inputForward) {
            targetSpeed = boostingNow ? maxForwardSpeed * def.boostMultiplier() : maxForwardSpeed;
        } else if (b.inputBackward) {
            targetSpeed = -maxForwardSpeed * 0.22f;
            accelerationRate = def.acceleration() * 0.85f;
        }
        targetSpeed = handling.modifyTargetSpeed(targetSpeed, b, def, boostingNow);
        accelerationRate = handling.modifyAcceleration(accelerationRate, b, def, boostingNow);

        if (targetSpeed != 0) {
            b.currentSpeed = Mth.lerp(accelerationRate, b.currentSpeed, targetSpeed);
        } else {
            float deceleration = handling.modifyDeceleration(def.deceleration(), b, def);
            b.currentSpeed = Mth.lerp(Mth.clamp(deceleration, 0f, 1f), b.currentSpeed, 0);
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

        // A broom the rider is not actively holding up sinks. weakGravity has been authored on
        // every definition, range-validated by the codec and printed by /wandb world broom info
        // since the definitions landed, and read by nothing at all — so a ridden broom hovered
        // forever and "landing" meant flying into the ground. Only applied when neither vertical
        // key is held: holding ascend or descend is the rider taking charge of altitude, and
        // sinking against a held ascend would just be a weaker ascent with extra arithmetic.
        if (!b.inputUp && !b.inputDown) {
            b.verticalVelocity = BroomFlightRules.applyWeakGravity(b.verticalVelocity,
                    handling.modifyWeakGravity(def.weakGravity(), b, def));
        }

        float preMoveSpeed = b.currentSpeed;
        float preMoveDescent = -b.verticalVelocity; // positive while falling
        Vec3 prevMotion = b.getDeltaMovement();
        // Momentum is per-broom now. BroomTuning's old COAST_DRAG and INPUT_DRAG are exactly what
        // momentumRetention 0.90 reproduces, which is what BALANCED hands an unauthored broom.
        float drag = (b.inputForward || b.inputBackward || b.inputUp || b.inputDown)
                ? def.handling().inputDrag()
                : def.handling().coastDrag();
        // Auto-stabilise: converge harder on where the broom is pointed, which is what "less drift"
        // means in this model. See SnidgetFeather#stabilise.
        float convergence = snidget ? SnidgetFeather.stabilise(def.lerpFactor()) : def.lerpFactor();
        double motX = Mth.lerp(convergence, prevMotion.x * drag, fwdX * b.currentSpeed);
        double motZ = Mth.lerp(convergence, prevMotion.z * drag, fwdZ * b.currentSpeed);
        // Last word on velocity, after lift, gravity and drag have all had theirs.
        handling.afterVelocityComputed(b, def);
        b.setDeltaMovement(motX, b.verticalVelocity, motZ);
        b.move(MoverType.SELF, b.getDeltaMovement());

        if (!b.level().isClientSide() && (b.horizontalCollision || b.verticalCollision)) {
            BroomImpacts.handleCollision(b, preMoveSpeed, preMoveDescent);
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
        b.rollTilt = HandlingProfileRegistry.of(def)
                .modifyRollTilt(Mth.lerp(stabilityLerp, b.rollTilt, targetRoll), b, def);

        // The other half of wobbleAtBoost: a visible roll shudder while the boost is firing. Purely
        // cosmetic — rollTilt is a render value — so it costs nothing in control authority beyond
        // the heading wander BroomMovement already applied.
        if (b.isBoostFiring()) {
            b.rollTilt += HandlingMath.boostRoll(b.tickCount, def.handling());
        }

        float speedRatio = Mth.clamp(Math.abs(b.currentSpeed) / def.maxSpeed(), 0f, 1f);
        // Negative = nose-down tuck at speed; fades when pitching steeply so it doesn't fight entity pitch rotation
        float pitchCos = Math.abs(Mth.cos(b.getXRot() * Mth.DEG_TO_RAD));
        float targetLean = -speedRatio * maxLeanAngle * pitchCos;
        b.forwardLean = Mth.lerp(stabilityLerp, b.forwardLean, targetLean);

        b.prevSpeed = b.currentSpeed;
        b.prevSteerYaw = b.getYRot();
    }
}
