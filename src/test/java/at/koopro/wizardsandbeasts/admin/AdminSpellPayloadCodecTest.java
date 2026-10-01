package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.network.spell.SpellTuningSyncS2CPayload;
import at.koopro.wizardsandbeasts.spell.tuning.SpellOverride;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuningSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The Magic section's wire formats, and the spell tuning sync every client receives. */
class AdminSpellPayloadCodecTest {

    @Test
    void theTuningSyncRoundTripsEveryField() {
        SpellOverride override = SpellOverride.NONE.withEnabled(Optional.of(false)).withCooldownTicks(Optional.of(12))
                .withDamage(Optional.of(3.5f)).withRange(Optional.of(24.0f))
                .withRequirement(Optional.of("wizards_and_beasts:lumos")).withRequiredSkill(Optional.of(""));
        SpellTuningSnapshot sent = new SpellTuningSnapshot(Map.of("wizards_and_beasts:stupefy", override),
                new SpellTuningSnapshot.Globals(1.5f, 0.75f, 2.0f, false, false));
        ByteBuf buf = Unpooled.buffer();
        SpellTuningSyncS2CPayload.STREAM_CODEC.encode(buf, new SpellTuningSyncS2CPayload(sent));
        SpellTuningSnapshot received = SpellTuningSyncS2CPayload.STREAM_CODEC.decode(buf).snapshot();
        assertEquals(sent.globals(), received.globals());
        assertEquals(override, received.override("wizards_and_beasts:stupefy"));
    }

    @Test
    void aNonFiniteMultiplierFromTheWireIsNeutralised() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeFloat(Float.NaN);
        buf.writeFloat(Float.POSITIVE_INFINITY);
        buf.writeFloat(1.0f);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        buf.writeInt(0);
        SpellTuningSnapshot.Globals globals = SpellTuningSyncS2CPayload.STREAM_CODEC.decode(buf).snapshot().globals();
        assertEquals(1.0f, globals.damageMultiplier());
        assertEquals(1.0f, globals.cooldownMultiplier());
    }

    @Test
    void spellPayloadsRoundTrip() {
        AdminSpellSummary summary = new AdminSpellSummary("wizards_and_beasts:crucio", "spell.x.name", "DARK_ARTS",
                "BEAM_CHANNEL", "UNFORGIVABLE", false, true, true, true, true, false, "none", 0xFF00FF, "beam");
        ByteBuf buf = Unpooled.buffer();
        AdminSpellPayloads.DetailReply sent = new AdminSpellPayloads.DetailReply(summary,
                List.of(new AdminSpellFact("a.b.c", "value", false)), List.of());
        AdminSpellPayloads.DetailReply.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, AdminSpellPayloads.DetailReply.STREAM_CODEC.decode(buf));

        ByteBuf test = Unpooled.buffer();
        AdminSpellPayloads.TestRequest request = new AdminSpellPayloads.TestRequest("wizards_and_beasts:stupefy",
                SpellTestService.TargetMode.PLAYER, UUID.randomUUID());
        AdminSpellPayloads.TestRequest.STREAM_CODEC.encode(test, request);
        assertEquals(request, AdminSpellPayloads.TestRequest.STREAM_CODEC.decode(test));
    }

    @Test
    void anUnknownTargetModeFallsBackToTheSafestOne() {
        ByteBuf buf = Unpooled.buffer();
        PacketCodecUtils.writeString(buf, "wizards_and_beasts:stupefy");
        PacketCodecUtils.writeString(buf, "EVERYONE_ON_THE_SERVER");
        buf.writeBoolean(false);
        AdminSpellPayloads.TestRequest decoded = AdminSpellPayloads.TestRequest.STREAM_CODEC.decode(buf);
        assertEquals(SpellTestService.TargetMode.SELF, decoded.mode(), "an unknown mode can only ever aim at the sender");
        assertNull(decoded.player());
    }
}
