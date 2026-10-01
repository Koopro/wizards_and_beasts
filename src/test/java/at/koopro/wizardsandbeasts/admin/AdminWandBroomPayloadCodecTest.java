package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.broom.BroomAdminService;
import at.koopro.wizardsandbeasts.admin.broom.BroomSettingProvider;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.admin.wand.WandSettingProvider;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import at.koopro.wizardsandbeasts.network.wand.WandGlobalsSyncS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Wands and Travel sections' wire formats and the ids their settings are addressed by. */
class AdminWandBroomPayloadCodecTest {

    private static final AdminSpellFact FACT = new AdminSpellFact("admin.wizards_and_beasts.wand_fact.rarity", "common", false);
    private static final WandAdminService.Request REQUEST = new WandAdminService.Request(
            "wizards_and_beasts:holly", "wizards_and_beasts:phoenix_feather", 11.0f, "SUPPLE", "wizards_and_beasts:harry");

    @Test
    void wandPayloadsRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        AdminWandPayloads.PartInfo holly = new AdminWandPayloads.PartInfo("wizards_and_beasts:holly",
                "wand_wood.wizards_and_beasts.holly", true, "Holly is one of the rarer kinds of wand woods.",
                List.of(FACT), 0xFFB08A5A);
        WandAdminService.Catalog catalog = new WandAdminService.Catalog(List.of(holly), List.of(),
                List.of("wizards_and_beasts:holly|wizards_and_beasts:phoenix_feather"),
                List.of("wizards_and_beasts:harry|Harry's Wand"), List.of());
        AdminWandPayloads.CatalogReply.STREAM_CODEC.encode(buf, new AdminWandPayloads.CatalogReply(catalog));
        assertEquals(catalog, AdminWandPayloads.CatalogReply.STREAM_CODEC.decode(buf).catalog());

        AdminWandPayloads.PreviewRequest.STREAM_CODEC.encode(buf, new AdminWandPayloads.PreviewRequest(REQUEST));
        assertEquals(REQUEST, AdminWandPayloads.PreviewRequest.STREAM_CODEC.decode(buf).request());

        AdminWandPayloads.PreviewReply reply = new AdminWandPayloads.PreviewReply(REQUEST,
                new AdminWandPayloads.Preview(true, "admin.wizards_and_beasts.wand_action.valid", List.of(FACT)));
        AdminWandPayloads.PreviewReply.STREAM_CODEC.encode(buf, reply);
        assertEquals(reply, AdminWandPayloads.PreviewReply.STREAM_CODEC.decode(buf));

        AdminWandPayloads.GiveRequest.STREAM_CODEC.encode(buf, new AdminWandPayloads.GiveRequest(REQUEST));
        assertEquals(REQUEST, AdminWandPayloads.GiveRequest.STREAM_CODEC.decode(buf).request());
        assertFalse(buf.isReadable());
    }

    @Test
    void broomListRoundTrips() {
        ByteBuf buf = Unpooled.buffer();
        AdminBroomPayloads.BroomSummary broom = new AdminBroomPayloads.BroomSummary("wizards_and_beasts:firebolt",
                "item.wizards_and_beasts.firebolt", "elite", true, false, 0xFF5A3A22,
                List.of(1.2f, 0.05f), List.of(1.2f, 0.05f), List.of(FACT));
        BroomAdminService.Listing listing = new BroomAdminService.Listing(List.of(broom), List.of());
        AdminBroomPayloads.ListReply.STREAM_CODEC.encode(buf, new AdminBroomPayloads.ListReply(listing));
        assertEquals(listing, AdminBroomPayloads.ListReply.STREAM_CODEC.decode(buf).listing());
    }

    @Test
    void wandGlobalsSyncClampsWhatItReads() {
        ByteBuf buf = Unpooled.buffer();
        WandGlobalsSyncS2CPayload sent = new WandGlobalsSyncS2CPayload(
                new at.koopro.wizardsandbeasts.wand.rules.WandGlobals.Values(1.5f, 2.0f, 2, 0.5f, false));
        WandGlobalsSyncS2CPayload.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, WandGlobalsSyncS2CPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void settingIdsDeriveTheirTexts() {
        Identifier wood = Identifier.fromNamespaceAndPath("wizards_and_beasts", "holly");
        Identifier core = Identifier.fromNamespaceAndPath("wizards_and_beasts", "phoenix_feather");
        Identifier pair = WandSettingProvider.pairId(wood, core);
        assertEquals("wand_pair/wizards_and_beasts/holly/wizards_and_beasts/phoenix_feather/enabled", pair.getPath());
        assertTrue(WandSettingProvider.isWandSetting(pair));
        assertTrue(WandSettingProvider.isWandSetting(WandSettingProvider.woodId(wood)));
        assertEquals("admin.wizards_and_beasts.wand_pair_property.enabled", AdminLangKeys.settingName(pair.getPath()));
        assertEquals("admin.wizards_and_beasts.wand_core_property.enabled",
                AdminLangKeys.settingName(WandSettingProvider.coreId(core).getPath()));

        Identifier speed = BroomSettingProvider.id(Identifier.fromNamespaceAndPath("wizards_and_beasts", "firebolt"), "max_speed");
        assertTrue(BroomSettingProvider.isBroomSetting(speed));
        assertFalse(WandSettingProvider.isWandSetting(speed));
        assertEquals("admin.wizards_and_beasts.broom_property.max_speed", AdminLangKeys.settingName(speed.getPath()));
        assertFalse(BroomSettingProvider.isBroomSetting(Identifier.fromNamespaceAndPath("wizards_and_beasts", "broom_speed_guard")));
    }
}
