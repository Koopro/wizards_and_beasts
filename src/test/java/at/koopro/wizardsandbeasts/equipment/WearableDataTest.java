package at.koopro.wizardsandbeasts.equipment;

import at.koopro.wizardsandbeasts.item.armor.WizardArmorMaterials;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contracts between the equipment code and its data files, none of which fail loudly at runtime.
 *
 * <ul>
 *   <li>A data-map entry that does not decode is dropped by NeoForge with a log line, and the item
 *       silently stops being wearable.</li>
 *   <li>An {@link EquipmentType} naming a slot no file declares — or a slot not assigned to players —
 *       is a type that can never be worn: Curios simply has no such slot on the player.</li>
 *   <li>The clothing pieces were armour before they were Curios; their stats moved into the data map
 *       by hand, and must keep matching the material they still report.</li>
 * </ul>
 */
class WearableDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path WEARABLES = DATA.resolve("data_maps/item/wearables.json");
    private static final Path ITEM_DEFINITIONS = Path.of("src/generated/resources/assets/wizards_and_beasts/items");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject values() throws IOException {
        return read(WEARABLES).getAsJsonObject("values");
    }

    @Test
    void everyEntryDecodes() throws IOException {
        for (Map.Entry<String, JsonElement> entry : values().entrySet()) {
            var result = WearableDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue());
            assertTrue(result.isSuccess(), entry.getKey() + ": " + result.error().map(Object::toString).orElse(""));
        }
    }

    @Test
    void everyEntryNamesARealItemOfThisMod() throws IOException {
        for (String id : values().keySet()) {
            assertTrue(id.startsWith("wizards_and_beasts:"), id + " is another mod's item; give it its own pack");
            String path = id.substring(id.indexOf(':') + 1);
            assertTrue(Files.exists(ITEM_DEFINITIONS.resolve(path + ".json")),
                    id + " has no item definition, so it is probably not a registered item");
        }
    }

    @Test
    void everyTypeSlotIsDeclaredAndGivenToPlayers() throws IOException {
        JsonArray assigned = read(DATA.resolve("curios/entities/player.json")).getAsJsonArray("slots");
        Set<String> playerSlots = new HashSet<>();
        assigned.forEach(e -> playerSlots.add(e.getAsString()));
        for (EquipmentType type : EquipmentType.values()) {
            Path slotFile = DATA.resolve("curios/slots/" + type.slotId() + ".json");
            assertTrue(Files.exists(slotFile), type + " names slot '" + type.slotId() + "' but no slot file declares it");
            assertTrue(playerSlots.contains(type.slotId()), type + "'s slot is not assigned to players");
            JsonArray validators = read(slotFile).getAsJsonArray("validators");
            assertTrue(validators.toString().contains("wizards_and_beasts:wearable"),
                    type.slotId() + " does not run the wearable validator, so defined items cannot enter it");
        }
    }

    @Test
    void ringSlotsAreMoreThanOne() throws IOException {
        assertTrue(read(DATA.resolve("curios/slots/ring.json")).get("size").getAsInt() >= 2);
    }

    @Test
    void clothingKeepsTheStatsItHadAsArmour() throws IOException {
        JsonObject values = values();
        assertStats(values, "student_robe_chest", WizardArmorMaterials.STUDENT_ROBE, ArmorType.CHESTPLATE);
        assertStats(values, "auror_robe_chest", WizardArmorMaterials.AUROR_ROBE, ArmorType.CHESTPLATE);
        assertStats(values, "death_eater_robe_chest", WizardArmorMaterials.DEATH_EATER_ROBE, ArmorType.CHESTPLATE);
        assertStats(values, "wizard_hat", WizardArmorMaterials.WIZARD_HAT, ArmorType.HELMET);
        for (String mask : new String[]{"death_eater_mask", "death_eater_mask_1", "death_eater_mask_2",
                "death_eater_mask_3", "death_eater_mask_4", "death_eater_mask_5"}) {
            assertStats(values, mask, WizardArmorMaterials.DEATH_EATER_MASK, ArmorType.HELMET);
        }
    }

    @Test
    void robesCoverTheChestAndOnlyTheHallowConceals() throws IOException {
        values().entrySet().forEach(entry -> {
            WearableDefinition definition = WearableDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
            if (definition.type() == EquipmentType.BODY_CLOTHING) {
                assertTrue(definition.hides().contains(net.minecraft.world.entity.EquipmentSlot.CHEST), entry.getKey());
            }
            assertEquals(entry.getKey().endsWith("deathly_hallow_cloak"), definition.concealsWearer(), entry.getKey());
        });
    }

    private static void assertStats(JsonObject values, String path, ArmorMaterial material, ArmorType type) {
        JsonObject entry = values.getAsJsonObject("wizards_and_beasts:" + path);
        assertFalse(entry == null, path + " missing from wearables.json");
        WearableDefinition definition = WearableDefinition.CODEC.parse(JsonOps.INSTANCE, entry).getOrThrow();
        assertEquals((double) material.defense().getOrDefault(type, 0), sum(definition, Attributes.ARMOR), path + " armour");
        assertEquals((double) material.toughness(), sum(definition, Attributes.ARMOR_TOUGHNESS), 1e-6, path + " toughness");
        assertEquals((double) material.knockbackResistance(), sum(definition, Attributes.KNOCKBACK_RESISTANCE), 1e-6,
                path + " knockback resistance");
    }

    private static double sum(WearableDefinition definition, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return definition.attributes().stream()
                .filter(m -> m.attribute().equals(attribute))
                .mapToDouble(WearableDefinition.Modifier::amount)
                .sum();
    }
}
