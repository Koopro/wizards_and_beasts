package at.koopro.wizardsandbeasts.broom;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * Where the rider sits, relative to the broom's own position.
 *
 * <h2>The default is negative, and that is not a typo</h2>
 * Two numbers decide where a rider straddles a broom, and neither is the entity's hitbox height:
 *
 * <ul>
 *   <li>the rider straddles the shaft where the shaft passes under them — at {@code z ≈ 0}, which is
 *       the {@code _mid} segment of the shaft chain. What matters is that segment's <b>top
 *       surface</b>, because a person sitting on a broomstick has it pressed under them, not running
 *       through them. For the default {@code plain} shaft that is {@code y = 5} model units,
 *       <b>0.3125 blocks</b>;</li>
 *   <li>a rendered humanoid's hip pivot sits <b>0.75 blocks</b> above its own position, because
 *       {@code LivingEntityRenderer} translates the model up by 1.501 and the leg part hangs 12 units
 *       back down from there. {@code BroomRiderRenderHandler} already banks the rider about that
 *       same 0.75.</li>
 * </ul>
 *
 * <p>So the default seat is {@code 0.3125 - 0.75 = }{@value #DEFAULT_SEAT_Y} blocks. Two earlier
 * values were wrong in different directions: {@code dimensions.height() * 0.55} put the hip 0.83
 * blocks <em>above</em> the handle, because a hitbox height says nothing about where a model draws
 * its shaft; and {@code -0.50}, measured to the shaft's centre line, sank every rider half a shaft
 * into the wood.
 *
 * <h2>It is per broom, because shafts are not all the same thickness</h2>
 * The rig ships shafts from two model units across to six at the point the rider sits. A single seat
 * height cannot be right for both: seated for the Firebolt's needle, a rider is buried to the knees
 * in the Oakshaft's log. Each shipped broom authors the value its own shaft asks for —
 * {@code -0.4375} for the thin shafts, {@code -0.375} for {@code oak}, {@code -0.3125} for
 * {@code heavy_oak} — and {@code BroomSeatParityTest} holds those against the geometry, so widening
 * a shaft without re-seating its broom fails the build.
 *
 * <p>The brief for this field specified a default of {@code (0, 0.55, 0)}. That is the same mistake
 * one step further on: as an absolute offset it seats the rider a full 1.30 blocks above a handle
 * whose top is drawn at 0.3125. The default here is the derived value instead, and every shipped
 * broom authors {@code passengerOffset} explicitly so the number is visible rather than inherited.
 *
 * <p>Because the seat sits <em>below</em> the broom's origin, {@code BroomItem} lifts a broom by
 * {@code -y} when it is mounted. Anything that seats a rider on a broom has to do the same, or the
 * rider arrives half a block inside the floor and gets pushed out.
 */
public record BroomSeat(Vec3 passengerOffset, float passengerYawOffset) {

    /** Top of the default {@code plain} shaft (0.3125) minus humanoid hip height (0.75). */
    public static final double DEFAULT_SEAT_Y = -0.4375;

    /** Straddling the shaft, facing the way the broom points. */
    public static final BroomSeat DEFAULT = new BroomSeat(new Vec3(0.0, DEFAULT_SEAT_Y, 0.0), 0.0f);

    static BroomSeat decode(BroomFields<?> fields) {
        return new BroomSeat(
                fields.optional("passengerOffset", Vec3.CODEC, DEFAULT.passengerOffset()),
                fields.rangedFloat("passengerYawOffset", -180.0f, 180.0f, DEFAULT.passengerYawOffset()));
    }

    /** How far a broom must be raised so a rider mounting it lands where they were standing. */
    public double mountLift() {
        return -passengerOffset.y;
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
