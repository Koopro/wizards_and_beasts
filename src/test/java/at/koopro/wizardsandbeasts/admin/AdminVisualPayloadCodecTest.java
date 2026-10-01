package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.PresetOp;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualSettingProvider;
import at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads;
import at.koopro.wizardsandbeasts.network.visual.BeamVisualSyncS2CPayload;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Visuals section's wire formats, the beam sync, the ids beam settings are addressed by, and their text. */
class AdminVisualPayloadCodecTest {

    private static final Map<String, String> VALUES = Map.of("core_width", "3", "core_color", "#AABBCC");

    @Test
    void listReplyRoundTrips() {
        BeamVisualAdminService.BeamSummary crucio = new BeamVisualAdminService.BeamSummary("crucio", 0xFFAA0000, true,
                true, "custom", Map.of("core_width", "1"), VALUES);
        BeamPreset preset = new BeamPreset("green_jet", "Green Jet", false, VALUES);
        BeamVisualAdminService.Listing listing = new BeamVisualAdminService.Listing(List.of(crucio), List.of(preset), List.of());
        ByteBuf buf = Unpooled.buffer();
        AdminVisualPayloads.ListReply.STREAM_CODEC.encode(buf, new AdminVisualPayloads.ListReply(listing));
        AdminVisualPayloads.ListReply decoded = AdminVisualPayloads.ListReply.STREAM_CODEC.decode(buf);
        assertEquals(listing.beams(), decoded.listing().beams());
        assertEquals(listing.presets(), decoded.listing().presets());
    }

    @Test
    void presetRequestAndReplyRoundTrip() {
        for (PresetOp op : PresetOp.values()) {
            ByteBuf buf = Unpooled.buffer();
            AdminVisualPayloads.PresetRequest request = new AdminVisualPayloads.PresetRequest(op, "green_jet", "Green Jet", VALUES);
            AdminVisualPayloads.PresetRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, AdminVisualPayloads.PresetRequest.STREAM_CODEC.decode(buf));

            ByteBuf reply = Unpooled.buffer();
            AdminVisualPayloads.ActionReply action = new AdminVisualPayloads.ActionReply(op, "name_taken", "green_jet");
            AdminVisualPayloads.ActionReply.STREAM_CODEC.encode(reply, action);
            assertEquals(action, AdminVisualPayloads.ActionReply.STREAM_CODEC.decode(reply));
        }
    }

    @Test
    void unknownPresetOpIsRefusedAtDecode() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(99);
        assertThrows(RuntimeException.class, () -> AdminVisualPayloads.PresetRequest.STREAM_CODEC.decode(buf));
    }

    @Test
    void syncRoundTripsAndRefusesOversizedTables() {
        Map<String, Map<String, String>> table = Map.of("crucio", VALUES, "aguamenti", Map.of("enabled", "false"));
        ByteBuf buf = Unpooled.buffer();
        BeamVisualSyncS2CPayload.STREAM_CODEC.encode(buf, new BeamVisualSyncS2CPayload(table));
        assertEquals(table, BeamVisualSyncS2CPayload.STREAM_CODEC.decode(buf).overrides());

        ByteBuf hostile = Unpooled.buffer();
        hostile.writeInt(1_000_000);
        assertThrows(RuntimeException.class, () -> BeamVisualSyncS2CPayload.STREAM_CODEC.decode(hostile));
    }

    @Test
    void settingIdsParseOnlyWhenWellFormed() {
        Identifier id = BeamVisualSettingProvider.id("crucio", BeamVisualProperty.CORE_WIDTH);
        assertEquals("wizards_and_beasts:beam/crucio/core_width", id.toString());
        BeamVisualSettingProvider.Parsed parsed = BeamVisualSettingProvider.parse(id);
        assertNotNull(parsed);
        assertEquals("crucio", parsed.spellKey());
        assertEquals(BeamVisualProperty.CORE_WIDTH, parsed.property());
        assertNull(BeamVisualSettingProvider.parse(Identifier.fromNamespaceAndPath("wizards_and_beasts", "beam/stupefy/core_width")));
        assertNull(BeamVisualSettingProvider.parse(Identifier.fromNamespaceAndPath("wizards_and_beasts", "beam/crucio/length")));
        assertNull(BeamVisualSettingProvider.parse(Identifier.fromNamespaceAndPath("wizards_and_beasts", "beam/crucio")));
        assertNull(BeamVisualSettingProvider.parse(Identifier.fromNamespaceAndPath("other", "beam/crucio/spin")));
    }

    @Test
    void everyBeamSettingAndPageKeyHasItsText() throws IOException {
        Set<String> keys;
        try (Reader reader = Files.newBufferedReader(Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
                "lang", "en_us.json"))) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            keys = json.keySet();
        }
        Set<String> missing = new TreeSet<>();
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            String path = BeamVisualSettingProvider.id(BeamVisualDefaults.CRUCIO, property).getPath();
            for (String key : List.of(AdminLangKeys.settingName(path), AdminLangKeys.settingDescription(path))) {
                if (!keys.contains(key)) {
                    missing.add(key);
                }
            }
        }
        for (String outcome : List.of("ok", "invalid_name", "name_taken", "not_found", "builtin", "limit",
                "invalid_values", "unauthorized")) {
            String key = "admin.wizards_and_beasts.beam.outcome." + outcome;
            if (!keys.contains(key)) {
                missing.add(key);
            }
        }
        for (String tab : List.of("beams", "particles", "impacts", "hud", "screen", "entities", "debug")) {
            String key = "admin.wizards_and_beasts.tab.visuals." + tab;
            if (!keys.contains(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), () -> "visuals lang keys missing:\n  " + String.join("\n  ", missing));
    }
}
