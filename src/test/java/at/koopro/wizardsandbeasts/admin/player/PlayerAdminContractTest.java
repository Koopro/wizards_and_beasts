package at.koopro.wizardsandbeasts.admin.player;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminPolicy;
import at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Players section promises without a server: money is its own authority, destructive actions are marked,
 * the wire formats round-trip and refuse an invented action, and every action, facet and outcome has its words.
 */
class PlayerAdminContractTest {

    @Test
    void moneyIsItsOwnAuthorityAndOnlyMoneyActionsNeedIt() {
        for (PlayerAdminAction action : PlayerAdminAction.values()) {
            boolean money = action == PlayerAdminAction.MONEY_DEPOSIT || action == PlayerAdminAction.MONEY_WITHDRAW;
            assertEquals(money ? AdminCapability.MONEY : AdminCapability.PLAYERS, action.capability(), action.id());
        }
        assertTrue(AdminPolicy.forVerdict(true).contains(AdminCapability.MONEY));
        assertFalse(AdminPolicy.forVerdict(false).contains(AdminCapability.MONEY));
    }

    @Test
    void everyActionThatTakesSomethingAwayIsDestructive() {
        for (PlayerAdminAction action : List.of(PlayerAdminAction.HERITAGE_ASSIGN, PlayerAdminAction.HERITAGE_RESET,
                PlayerAdminAction.SPELL_GRANT, PlayerAdminAction.SPELL_REVOKE, PlayerAdminAction.SPELL_RESET_PROGRESS,
                PlayerAdminAction.SKILL_RESET, PlayerAdminAction.EFFECTS_CLEAR, PlayerAdminAction.MINISTRY_PARDON,
                PlayerAdminAction.MINISTRY_WAIVE_FINE, PlayerAdminAction.MONEY_DEPOSIT, PlayerAdminAction.MONEY_WITHDRAW)) {
            assertTrue(action.destructive(), action.id());
        }
        assertFalse(PlayerAdminAction.EFFECT_PREVIEW.destructive());
        assertFalse(PlayerAdminAction.SPELL_UNLOCK.destructive());
    }

    @Test
    void anActionRoundTripsAndAnInventedOneIsRefused() {
        for (PlayerAdminAction action : PlayerAdminAction.values()) {
            AdminPlayerPayloads.ActionRequest sent = new AdminPlayerPayloads.ActionRequest(UUID.randomUUID(), action,
                    "galleons", 12, true);
            ByteBuf buf = Unpooled.buffer();
            AdminPlayerPayloads.ActionRequest.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, AdminPlayerPayloads.ActionRequest.STREAM_CODEC.decode(buf));
        }
        ByteBuf crafted = Unpooled.buffer();
        crafted.writeLong(1L);
        crafted.writeLong(2L);
        crafted.writeByte(PlayerAdminAction.values().length + 4);
        assertThrows(RuntimeException.class, () -> AdminPlayerPayloads.ActionRequest.STREAM_CODEC.decode(crafted));
    }

    @Test
    void anOverlongArgumentIsCutAtDecode() {
        AdminPlayerPayloads.ActionRequest sent = new AdminPlayerPayloads.ActionRequest(UUID.randomUUID(),
                PlayerAdminAction.SPELL_GRANT, "x".repeat(1000), 0, true);
        ByteBuf buf = Unpooled.buffer();
        AdminPlayerPayloads.ActionRequest.STREAM_CODEC.encode(buf, sent);
        assertTrue(AdminPlayerPayloads.ActionRequest.STREAM_CODEC.decode(buf).argument().length() <= 128);
    }

    @Test
    void aFacetAndASearchRoundTrip() {
        UUID player = UUID.randomUUID();
        AdminPlayerPayloads.FacetReply facet = new AdminPlayerPayloads.FacetReply(new PlayerAdminService.FacetView(
                PlayerAdminService.Facet.SPELLS, player, "Hermione",
                List.of(new AdminSpellFact("admin.x", "3", false)),
                List.of(new PlayerAdminService.Item("wizards_and_beasts:protego", "spell.protego", true, "40%", true)),
                List.of(new PlayerAdminService.Option("wizards_and_beasts:lumos", "spell.lumos", true))), true);
        ByteBuf buf = Unpooled.buffer();
        AdminPlayerPayloads.FacetReply.STREAM_CODEC.encode(buf, facet);
        assertEquals(facet, AdminPlayerPayloads.FacetReply.STREAM_CODEC.decode(buf));

        AdminPlayerPayloads.SearchReply search = new AdminPlayerPayloads.SearchReply(List.of(new PlayerAdminService.PlayerRow(
                player, "Hermione", "Wizardkind", "Muggle-born", 20f, 20f, 1, "clear", "", "survival")), false, true);
        buf = Unpooled.buffer();
        AdminPlayerPayloads.SearchReply.STREAM_CODEC.encode(buf, search);
        assertEquals(search, AdminPlayerPayloads.SearchReply.STREAM_CODEC.decode(buf));
    }

    @Test
    void everyActionFacetAndOutcomeHasItsWords() throws Exception {
        JsonObject lang;
        try (InputStreamReader reader = new InputStreamReader(PlayerAdminContractTest.class.getResourceAsStream(
                "/assets/wizards_and_beasts/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String k = "admin.wizards_and_beasts.players.";
        assertNotNull(lang.get("admin.wizards_and_beasts.section.players"));
        for (PlayerAdminAction action : PlayerAdminAction.values()) {
            assertNotNull(lang.get(k + "action." + action.id()), action.id());
            if (action.destructive()) {
                assertNotNull(lang.get(k + "confirm." + action.id()), "confirm " + action.id());
            }
        }
        for (PlayerAdminService.Facet facet : PlayerAdminService.Facet.values()) {
            assertNotNull(lang.get(k + "tab." + facet.id()), facet.id());
            assertNotNull(lang.get(k + "facet." + facet.id()), facet.id());
        }
        for (String code : List.of("unauthorized", "no_player", "invalid_argument", "confirm_required", "not_eligible",
                "nothing_to_do", "insufficient", "failed")) {
            assertNotNull(lang.get(k + "outcome." + code), code);
        }
    }
}
