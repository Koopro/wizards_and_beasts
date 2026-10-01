package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingResultS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire formats. A crafted packet is the attack surface, so decoding must never produce something the
 * server would treat as more than it is — and a descriptor must survive the trip unchanged.
 */
class AdminPayloadCodecTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    @Test
    void aChangeRequestRoundTrips() {
        ByteBuf buf = Unpooled.buffer();
        AdminChangeSettingC2SPayload sent = new AdminChangeSettingC2SPayload(12, id("perf_profile"), "HIGH", true);
        AdminChangeSettingC2SPayload.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, AdminChangeSettingC2SPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void aMalformedIdCannotAliasOneOfThisModsSettings() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        PacketCodecUtils.writeString(buf, "../../Not An Id!");
        PacketCodecUtils.writeString(buf, "1");
        buf.writeBoolean(false);
        // Decoding neither throws nor yields null; whatever comes out names nothing the registry holds, so the
        // service answers UNKNOWN_SETTING (or UNAUTHORIZED) like any other stranger's id.
        Identifier decoded = AdminChangeSettingC2SPayload.STREAM_CODEC.decode(buf).settingId();
        assertTrue(decoded.equals(AdminSettingService.NO_SETTING)
                        || !decoded.getNamespace().equals(WizardsAndBeastsMod.MODID),
                () -> "a mangled id decoded into this mod's namespace: " + decoded);
    }

    @Test
    void anOversizedValueIsRefusedAtDecode() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        PacketCodecUtils.writeString(buf, id("count").toString());
        buf.writeInt(PacketCodecUtils.MAX_STRING_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> AdminChangeSettingC2SPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void anUnknownResultStatusReadsAsRejected() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(3);
        PacketCodecUtils.writeString(buf, id("count").toString());
        PacketCodecUtils.writeString(buf, "TOTALLY_APPLIED");
        PacketCodecUtils.writeString(buf, "");
        PacketCodecUtils.writeString(buf, "");
        PacketCodecUtils.writeString(buf, "1");
        PacketCodecUtils.writeString(buf, "2");
        buf.writeBoolean(false);
        AdminSettingResultS2CPayload decoded = AdminSettingResultS2CPayload.STREAM_CODEC.decode(buf);
        assertTrue(decoded.result().rejected(), "garbage must never read as success");
    }

    @Test
    void aResultRoundTripsWithItsRejection() {
        AdminResult result = new AdminResult(id("count"), AdminResult.Status.REJECTED, AdminRejection.OUT_OF_RANGE,
                null, "5", "5", false);
        ByteBuf buf = Unpooled.buffer();
        AdminSettingResultS2CPayload.STREAM_CODEC.encode(buf, new AdminSettingResultS2CPayload(4, result));
        assertEquals(new AdminSettingResultS2CPayload(4, result), AdminSettingResultS2CPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void aDescriptorRoundTrips() {
        AdminSettingDescriptor descriptor = new AdminSettingDescriptor(id("perf_profile"), AdminCategory.PERFORMANCE,
                SettingKind.ENUM, SettingScope.SERVER, ApplyMode.NEW_CHUNKS, true, true, Double.NaN, Double.NaN, Double.NaN,
                List.of("LOW", "MEDIUM", "HIGH"), 0, "MEDIUM", "HIGH");
        ByteBuf buf = Unpooled.buffer();
        AdminSettingDescriptor.write(buf, descriptor);
        AdminSettingDescriptor decoded = AdminSettingDescriptor.read(buf);
        assertEquals(descriptor.id(), decoded.id());
        assertEquals(descriptor.options(), decoded.options());
        assertEquals(descriptor.value(), decoded.value());
        assertEquals(descriptor.dangerous(), decoded.dangerous());
        assertEquals(descriptor.category(), decoded.category());
        assertEquals(ApplyMode.NEW_CHUNKS, decoded.applyMode(), "the apply mode must survive the wire");
        for (ApplyMode mode : ApplyMode.values()) {
            AdminSettingDescriptor each = descriptor.withValue("LOW");
            each = new AdminSettingDescriptor(each.id(), each.category(), each.kind(), each.scope(), mode, each.dangerous(),
                    each.editable(), each.min(), each.max(), each.step(), each.options(), each.maxLength(),
                    each.defaultValue(), each.value());
            ByteBuf again = Unpooled.buffer();
            AdminSettingDescriptor.write(again, each);
            assertEquals(mode, AdminSettingDescriptor.read(again).applyMode());
        }
        assertEquals(ApplyMode.RUNTIME, ApplyMode.byOrdinal(99), "an unknown mode reads as runtime, never crashes");
    }
}
