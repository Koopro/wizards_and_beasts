package at.koopro.wizardsandbeasts.broom;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * Where a rider sits on a broom's model, and so how high that model is drawn.
 *
 * <h2>The rider stands on the broom's position; the model rises to meet them</h2>
 * A broom entity's position is its rider's feet — see {@link BroomGeometry}. What a definition authors is
 * where on the <em>model</em> the rider sits: {@code passengerOffset.y} is the seat's height above the
 * rendered model's origin, which is the top surface of the shaft's {@code _mid} segment at
 * {@link BroomGeometry#MODEL_SCALE}. The renderer lifts the model by {@link #modelLift()} so that surface
 * meets the rider's hip.
 *
 * <p>Shafts are not all the same thickness, so it is per broom: {@code 0.15625} for the five-unit shafts
 * ({@code plain}, {@code swept}, {@code racing}), {@code 0.1875} for {@code oak}, {@code 0.21875} for
 * {@code heavy_oak}. {@code BroomSeatParityTest} holds every shipped value against the geometry, so widening
 * a shaft without re-seating its broom fails the build.
 *
 * <h2>Why the number is authored this way</h2>
 * It has been wrong three different ways. {@code dimensions.height() * 0.55} put the hip 0.83 above the
 * handle; measuring to the shaft's centre line sank the rider half a shaft into it; and the
 * {@code shaftTop - 0.75} derivation that replaced both never subtracted the 0.6 that
 * {@code Entity.positionRider} takes off for a player's own vehicle attachment, so every rider sat 0.6
 * blocks inside the broom. Authoring a height on the model and letting the entity place the feet removes
 * the arithmetic that kept going wrong. A negative value is from the old frame and is rejected by range
 * rather than silently reinterpreted.
 *
 * <p>{@code x} and {@code z} nudge the rider along the broom's own axes, {@code +z} toward the bristles.
 */
public record BroomSeat(Vec3 passengerOffset, float passengerYawOffset) {

    /** Top of the default {@code plain} shaft's {@code _mid} segment — five model units — at render scale. */
    public static final double DEFAULT_SEAT_Y = 0.15625;

    /** Straddling the shaft, facing the way the broom points. */
    public static final BroomSeat DEFAULT = new BroomSeat(new Vec3(0.0, DEFAULT_SEAT_Y, 0.0), 0.0f);

    static BroomSeat decode(BroomFields<?> fields) {
        Vec3 offset = fields.optional("passengerOffset", Vec3.CODEC, DEFAULT.passengerOffset());
        if (offset.y < 0.0 || offset.y > BroomGeometry.HIP_HEIGHT) {
            fields.fault("passengerOffset y is the seat's height above the model origin and must be in [0, "
                    + BroomGeometry.HIP_HEIGHT + "]: " + offset.y
                    + " (a negative value is from the seat frame used before 2026-09-11)");
            offset = DEFAULT.passengerOffset();
        }
        return new BroomSeat(offset,
                fields.rangedFloat("passengerYawOffset", -180.0f, 180.0f, DEFAULT.passengerYawOffset()));
    }

    /** How far the model is drawn above the broom's position, so its seat meets the rider's hip. */
    public double modelLift() {
        return BroomGeometry.HIP_HEIGHT - passengerOffset.y;
    }

    <T> void encode(RecordBuilder<T> builder, DynamicOps<T> ops) {
        if (!passengerOffset.equals(DEFAULT.passengerOffset())) {
            builder.add("passengerOffset", Vec3.CODEC.encodeStart(ops, passengerOffset).result().orElseThrow());
        }
        if (passengerYawOffset != DEFAULT.passengerYawOffset()) {
            builder.add("passengerYawOffset", ops.createFloat(passengerYawOffset));
        }
    }
}
