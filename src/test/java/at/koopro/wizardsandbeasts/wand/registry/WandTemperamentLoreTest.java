package at.koopro.wizardsandbeasts.wand.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each shipped wood and core behaves the way the wandlore it cites says it does.
 *
 * <p>This pins <em>readings</em>, not numbers: that ash clings to its master, that dragon heartstring can be won,
 * that unicorn hair resents the Dark Arts. The trait each assertion reads is quoted in the test name and in the
 * definition's {@code _lore}, which must say which part is canon and which is gameplay. A definition that loses
 * its reading fails here, not in a playtest months later.
 */
class WandTemperamentLoreTest {

    private static final Path ROOT = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts");

    // ── woods (Ollivander's wood notes) ─────────────────────────────────────────────────────────

    @Test
    void ash_cleavesToItsOneTrueMasterAndLosesPowerWhenPassedOn() throws IOException {
        WandTemperament ash = wood("ash");
        assertTrue(ash.passedOnPower() < 1.0f);
        assertTrue(ash.foreignHandPower() < 1.0f);
        assertTrue(ash.extraWins() > 0);
    }

    @Test
    void blackthorn_bondsOnlyThroughDanger() throws IOException {
        assertTrue(wood("blackthorn").bondNeedsDanger());
    }

    @Test
    void elder_scornsAnOwnerWhoIsNotTheSuperior() throws IOException {
        assertTrue(wood("elder").extraWins() < 0);
    }

    @Test
    void hawthorn_backfiresWhenBadlyHandled() throws IOException {
        assertTrue(wood("hawthorn").backfiresInForeignHands());
    }

    @Test
    void rowan_neverServedADarkWizard() throws IOException {
        assertTrue(wood("rowan").darkArtsBondCost() > 0.0f);
    }

    @Test
    void vine_isTheMostSensitiveToItsMatch() throws IOException {
        assertTrue(wood("vine").bondGrowth() > 1.0f);
    }

    // ── cores (Ollivander's core notes; the rest are marked in their _lore) ───────────────────────

    @Test
    void unicornHair_isTheMostFaithfulAndHardestToTurnDark() throws IOException {
        WandTemperament unicorn = core("unicorn_hair");
        assertTrue(unicorn.extraWins() > 0);
        assertTrue(unicorn.darkArtsBondCost() > 0.0f);
    }

    @Test
    void dragonHeartstring_changesAllegianceIfWonAndBondsStronglyWithItsCurrentOwner() throws IOException {
        WandTemperament dragon = core("dragon_heartstring");
        assertTrue(dragon.extraWins() < 0);
        assertTrue(dragon.transferBondBonus() > 0.0f);
        assertTrue(dragon.bondGrowth() > 1.0f, "learns quickly");
    }

    @Test
    void phoenixFeather_isThePickiestAndItsAllegianceHardWon() throws IOException {
        WandTemperament phoenix = core("phoenix_feather");
        assertTrue(phoenix.bondGrowth() < 1.0f);
        assertTrue(phoenix.extraWins() > 0);
        assertTrue(phoenix.loyalCooldown() < 1.0f, "the greatest range of magic, revealed with time");
    }

    @Test
    void thestralTailHair_isMasteredOnlyByOneWhoHasSeenDeath() throws IOException {
        assertTrue(core("thestral_tail_hair").masteryNeedsDeathWitness());
    }

    @Test
    void thunderbirdTailFeather_isDifficultToMaster() throws IOException {
        assertTrue(core("thunderbird_tail_feather").bondGrowth() < 1.0f);
    }

    // ── every definition ────────────────────────────────────────────────────────────────────────

    /** Every definition says what it reads, and says where canon stops and gameplay begins. */
    @Test
    void everyDefinition_citesItsLoreAndMarksItsGameplay() throws IOException {
        for (String folder : new String[]{"wand_woods", "wand_cores"}) {
            for (Map.Entry<String, JsonObject> e : raw(folder).entrySet()) {
                JsonElement lore = e.getValue().get("_lore");
                assertTrue(lore != null && lore.isJsonPrimitive(), folder + "/" + e.getKey() + " has no _lore");
                String text = lore.getAsString();
                assertTrue(text.contains("Gameplay"), folder + "/" + e.getKey() + " does not mark its gameplay reading");
            }
        }
    }

    /** A wand is not a stat stick: no wood or core adds more than a tenth of damage either way. */
    @Test
    void noDefinition_isAStatStick() throws IOException {
        for (String folder : new String[]{"wand_woods", "wand_cores"}) {
            for (Map.Entry<String, JsonObject> e : raw(folder).entrySet()) {
                WandCastModifiers mods = WandCastModifiers.CODEC
                        .parse(JsonOps.INSTANCE, e.getValue().get("cast_modifiers")).getOrThrow();
                assertTrue(Math.abs(mods.damage() - 1.0f) <= 0.10f + 1.0e-4f,
                        folder + "/" + e.getKey() + " multiplies damage by " + mods.damage());
                for (float bonus : mods.categoryDamageBonus().values()) {
                    assertTrue(Math.abs(bonus) <= 0.10f + 1.0e-4f, folder + "/" + e.getKey() + " category bonus " + bonus);
                }
            }
        }
    }

    @Test
    void anOrdinaryWood_hasANeutralTemperament() throws IOException {
        assertEquals(WandTemperament.NEUTRAL, wood("holly"));
        assertFalse(wood("yew").backfiresInForeignHands());
    }

    private static WandTemperament wood(String id) throws IOException {
        return WandWoodDefinition.CODEC.parse(JsonOps.INSTANCE, raw("wand_woods").get(id)).getOrThrow().temperament();
    }

    private static WandTemperament core(String id) throws IOException {
        return WandCoreDefinition.CODEC.parse(JsonOps.INSTANCE, raw("wand_cores").get(id)).getOrThrow().temperament();
    }

    private static Map<String, JsonObject> raw(String folder) throws IOException {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(ROOT.resolve(folder))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                out.put(path.getFileName().toString().replace(".json", ""),
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            }
        }
        return out;
    }
}
