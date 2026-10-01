package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRule;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritagePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.heritage.HeritageRulesSyncS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Heritages section's wire formats, the rules sync, and the ids heritage rules are addressed by. */
class AdminHeritagePayloadCodecTest {

    @Test
    void theRulesSyncRoundTrips() {
        Map<String, HeritageRule> sent = Map.of(
                "veela", new HeritageRule(Optional.of(false), Optional.of(false)),
                "giant", HeritageRule.NONE.withSelectable(Optional.of(true)));
        ByteBuf buf = Unpooled.buffer();
        HeritageRulesSyncS2CPayload.STREAM_CODEC.encode(buf, new HeritageRulesSyncS2CPayload(sent));
        assertEquals(sent, HeritageRulesSyncS2CPayload.STREAM_CODEC.decode(buf).rules());
    }

    @Test
    void playerRequestsRoundTrip() {
        UUID player = UUID.randomUUID();
        ByteBuf buf = Unpooled.buffer();
        AdminHeritagePayloads.AssignRequest assign = new AdminHeritagePayloads.AssignRequest(player, "goblin", "goblin_pure", true);
        AdminHeritagePayloads.AssignRequest.STREAM_CODEC.encode(buf, assign);
        assertEquals(assign, AdminHeritagePayloads.AssignRequest.STREAM_CODEC.decode(buf));

        AdminHeritagePayloads.ResetOnboardingRequest reset = new AdminHeritagePayloads.ResetOnboardingRequest(player, false);
        AdminHeritagePayloads.ResetOnboardingRequest.STREAM_CODEC.encode(buf, reset);
        assertEquals(reset, AdminHeritagePayloads.ResetOnboardingRequest.STREAM_CODEC.decode(buf));
    }

    @Test
    void repliesRoundTrip() {
        UUID player = UUID.randomUUID();
        ByteBuf buf = Unpooled.buffer();
        AdminHeritagePayloads.PlayersReply players = new AdminHeritagePayloads.PlayersReply(List.of(
                new HeritageAdminService.PlayerRow(player, "Dev", "wizardkind", "half_blood"),
                new HeritageAdminService.PlayerRow(UUID.randomUUID(), "New", "", "")));
        AdminHeritagePayloads.PlayersReply.STREAM_CODEC.encode(buf, players);
        assertEquals(players, AdminHeritagePayloads.PlayersReply.STREAM_CODEC.decode(buf));

        HeritageAdminService.Inspection inspection = new HeritageAdminService.Inspection(player, "Dev", "wizardkind",
                "half_blood", List.of(new AdminSpellFact("a", "b", true)),
                List.of(new AdminSpellFact("stat.x", "12", false)), List.of(new AdminSpellFact("attr", "20", false)));
        AdminHeritagePayloads.InspectReply.STREAM_CODEC.encode(buf, new AdminHeritagePayloads.InspectReply(inspection));
        assertEquals(inspection, AdminHeritagePayloads.InspectReply.STREAM_CODEC.decode(buf).inspection());
    }

    @Test
    void ruleIdsAreDerivedAndResolvable() {
        for (Heritage heritage : Heritage.values()) {
            List<HeritageRuleSettings.Property> properties = HeritageRuleSettings.applicable(heritage);
            assertTrue(properties.contains(HeritageRuleSettings.Property.SELECTABLE), heritage.getId());
            assertEquals(HeritageTransformService.SERVED.contains(heritage),
                    properties.contains(HeritageRuleSettings.Property.TRANSFORMATION), heritage.getId());
            for (HeritageRuleSettings.Property property : properties) {
                Identifier id = HeritageRuleSettings.id(heritage, property);
                assertEquals(heritage, HeritageRuleSettings.heritageOf(id));
                assertTrue(AdminLangKeys.entityScoped(id.getPath()));
                assertEquals("admin.wizards_and_beasts.heritage_property." + property.path(),
                        AdminLangKeys.settingName(id.getPath()));
            }
        }
        assertNull(HeritageRuleSettings.heritageOf(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage/nobody/selectable")));
        assertNull(HeritageRuleSettings.heritageOf(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage/veela/wings")));
        assertFalse(AdminLangKeys.entityScoped("module_heritage"));
        // The spell family keeps its keys under the generalised derivation.
        assertEquals("admin.wizards_and_beasts.spell_property.cooldown_ticks",
                AdminLangKeys.settingName("spell/wizards_and_beasts/stupefy/cooldown_ticks"));
    }
}
