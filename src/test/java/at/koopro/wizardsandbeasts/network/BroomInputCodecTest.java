package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.network.broom.BroomInputC2SPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BroomInputCodecTest {

    @Test
    void roundTrip_preservesSequenceAndInputFlags() {
        BroomInputC2SPayload original = new BroomInputC2SPayload(42, true, false, true, false, true, 90.0f, -12.5f);
        ByteBuf buf = Unpooled.buffer();
        try {
            BroomInputC2SPayload.STREAM_CODEC.encode(buf, original);
            BroomInputC2SPayload decoded = BroomInputC2SPayload.STREAM_CODEC.decode(buf);
            assertEquals(original.sequence(), decoded.sequence());
            assertEquals(original.forward(), decoded.forward());
            assertEquals(original.backward(), decoded.backward());
            assertEquals(original.up(), decoded.up());
            assertEquals(original.down(), decoded.down());
            assertEquals(original.boosting(), decoded.boosting());
            assertEquals(original.yaw(), decoded.yaw());
            assertEquals(original.pitch(), decoded.pitch());
        } finally {
            buf.release();
        }
    }
}
