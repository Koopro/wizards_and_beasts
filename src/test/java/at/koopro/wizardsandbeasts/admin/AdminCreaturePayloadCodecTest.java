package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.admin.creature.CreatureRuleSettings;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Creatures section's wire formats and the ids creature rules are addressed by. */
class AdminCreaturePayloadCodecTest {

    private static final AdminCreaturePayloads.CreatureSummary SUMMARY = new AdminCreaturePayloads.CreatureSummary(
            "hippogriff", "entity.wizards_and_beasts.hippogriff", "WINGED_BEAST", true, "NEUTRAL", true, true, false,
            false, 2, 5, "wizards_and_beasts:textures/bestiary/icons/hippogriff.png", false);

    @Test
    void listAndDetailRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        AdminCreaturePayloads.ListReply list = new AdminCreaturePayloads.ListReply(List.of(SUMMARY));
        AdminCreaturePayloads.ListReply.STREAM_CODEC.encode(buf, list);
        assertEquals(list, AdminCreaturePayloads.ListReply.STREAM_CODEC.decode(buf));

        CreatureAdminService.Detail detail = new CreatureAdminService.Detail(SUMMARY,
                List.of(new AdminSpellFact("a", "20", false)), List.of(new AdminSpellFact("b", "c", true)),
                List.of("enrage"),
                List.of(new AdminCreaturePayloads.SpawnEntry("#minecraft:is_forest", 3, 1, 2, "wizards_and_beasts:spawn_x")),
                List.of("light_day"),
                List.of(new AdminCreaturePayloads.VariantInfo("bronze", "wizards_and_beasts:textures/entity/hippogriff/bronze.png", 1, 4, true)));
        AdminCreaturePayloads.DetailReply.STREAM_CODEC.encode(buf, new AdminCreaturePayloads.DetailReply(detail));
        assertEquals(detail, AdminCreaturePayloads.DetailReply.STREAM_CODEC.decode(buf).detail());
    }

    @Test
    void requestsCarryNoPosition() {
        ByteBuf buf = Unpooled.buffer();
        AdminCreaturePayloads.SpawnRequest spawn = new AdminCreaturePayloads.SpawnRequest("kelpie", "blue_black", true);
        AdminCreaturePayloads.SpawnRequest.STREAM_CODEC.encode(buf, spawn);
        assertEquals(spawn, AdminCreaturePayloads.SpawnRequest.STREAM_CODEC.decode(buf));
        // Three fields and nothing else: a creature, a variant, a switch. The server chooses where.
        assertEquals(3, AdminCreaturePayloads.SpawnRequest.class.getRecordComponents().length);
    }

    @Test
    void ruleIdsDeriveTheirTextKeys() {
        Identifier natural = CreatureRuleSettings.naturalSpawnId("unicorn");
        assertEquals("admin.wizards_and_beasts.creature_property.natural_spawn", AdminLangKeys.settingName(natural.getPath()));
        Identifier weight = CreatureRuleSettings.variantId("niffler", "pale", CreatureRuleSettings.WEIGHT);
        assertEquals("admin.wizards_and_beasts.creature_property.weight", AdminLangKeys.settingName(weight.getPath()));
        assertTrue(AdminLangKeys.entityScoped(weight.getPath()));
        assertEquals("niffler", CreatureRuleSettings.creatureOf(weight));
        assertNull(CreatureRuleSettings.creatureOf(Identifier.fromNamespaceAndPath("wizards_and_beasts", "creature/nobody/natural_spawn")));
    }
}
