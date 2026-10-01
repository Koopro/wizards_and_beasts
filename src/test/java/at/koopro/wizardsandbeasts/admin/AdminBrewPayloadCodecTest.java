package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.brew.BrewAdminService;
import at.koopro.wizardsandbeasts.admin.brew.BrewSettingIds;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The Brewing section's wire formats and the ids brew values are addressed by. */
class AdminBrewPayloadCodecTest {

    private static final AdminBrewPayloads.BrewSummary SUMMARY = new AdminBrewPayloads.BrewSummary(
            "wizards_and_beasts:felix_felicis", "brew.wizards_and_beasts.felix_felicis.name", 0xF2C94C, true,
            "wizards_and_beasts:felix_felicis", "COPPER", 1200, 0.4f, "MASTER", List.of("minecraft:luck"),
            "luck 180s", false, false, true);

    @Test
    void listAndDetailRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        AdminBrewPayloads.ListReply list = new AdminBrewPayloads.ListReply(List.of(SUMMARY));
        AdminBrewPayloads.ListReply.STREAM_CODEC.encode(buf, list);
        assertEquals(list, AdminBrewPayloads.ListReply.STREAM_CODEC.decode(buf));

        BrewAdminService.Detail detail = new BrewAdminService.Detail(SUMMARY,
                List.of(new AdminSpellFact("a", "b", false)), List.of("felix_felicis · on_drink"), List.of());
        AdminBrewPayloads.DetailReply.STREAM_CODEC.encode(buf, new AdminBrewPayloads.DetailReply(detail));
        assertEquals(detail, AdminBrewPayloads.DetailReply.STREAM_CODEC.decode(buf).detail());
    }

    @Test
    void settingIdsRoundTripAndDeriveTheirTexts() {
        Identifier effects = BrewSettingIds.brew("wizards_and_beasts:amortentia", BrewSettingIds.EFFECTS);
        BrewSettingIds.Parsed parsed = BrewSettingIds.parse(effects);
        assertEquals(BrewSettingIds.Kind.BREW, parsed.kind());
        assertEquals("wizards_and_beasts:amortentia", parsed.target());
        assertEquals("effects", parsed.property());

        Identifier ingredient = BrewSettingIds.ingredient("wizards_and_beasts:felix_felicis", "minecraft:gold_ingot");
        BrewSettingIds.Parsed ing = BrewSettingIds.parse(ingredient);
        assertEquals(BrewSettingIds.Kind.RECIPE, ing.kind());
        assertEquals("wizards_and_beasts:felix_felicis", ing.target());
        assertEquals("minecraft:gold_ingot", ing.item());
        assertEquals("admin.wizards_and_beasts.brew_recipe_property.ingredient", AdminLangKeys.settingName(ingredient.getPath()));
        assertEquals("admin.wizards_and_beasts.brew_property.effects", AdminLangKeys.settingName(effects.getPath()));
        assertNull(BrewSettingIds.parse(Identifier.fromNamespaceAndPath("wizards_and_beasts", "brew_speed_multiplier")));
        // Creature and heritage ids keep their keys under the dot rule.
        assertEquals("admin.wizards_and_beasts.creature_property.weight",
                AdminLangKeys.settingName("creature/niffler/variant/pale/weight"));
    }
}
