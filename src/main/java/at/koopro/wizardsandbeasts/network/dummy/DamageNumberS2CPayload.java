package at.koopro.wizardsandbeasts.network.dummy;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

/**
 * Server -&gt; Client: one number to float off a duelling dummy.
 *
 * <p>Carries a world position rather than an entity id. The number belongs to the moment of the
 * hit, not to the dummy: it stays where the blow landed while the dummy keeps being hit, and a
 * dummy dismantled mid-flight leaves its last numbers to finish rising rather than deleting them.
 *
 * <p>{@code amount} is signed - positive is damage, negative is healing - and is the figure
 * <em>applied</em> after armour and absorption. {@code colour} is resolved server-side from the
 * damage type; see {@code DummyDamageColours} for why that mapping does not live on the client.
 */
@NullMarked
public record DamageNumberS2CPayload(double x, double y, double z, float amount, int colour)
        implements CustomPacketPayload {

    public static final Type<DamageNumberS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dummy_damage_number"));

    public static final StreamCodec<ByteBuf, DamageNumberS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DamageNumberS2CPayload decode(ByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float amount = buf.readFloat();
            int colour = buf.readInt();
            return new DamageNumberS2CPayload(x, y, z, amount, colour);
        }

        @Override
        public void encode(ByteBuf buf, DamageNumberS2CPayload payload) {
            buf.writeDouble(payload.x);
            buf.writeDouble(payload.y);
            buf.writeDouble(payload.z);
            buf.writeFloat(payload.amount);
            buf.writeInt(payload.colour);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
