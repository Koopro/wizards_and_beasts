package at.koopro.wizardsandbeasts.spell.revelio;

import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import at.koopro.wizardsandbeasts.spell.def.SpellImplementationState;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How far and how long, from the shipped JSON — and what a bad JSON cannot do. */
class RevelioTest {

    private static final Path SHIPPED = Path.of(
            "src", "main", "resources", "data", "wizards_and_beasts", "spells", "revelio.json");

    private static SpellDefinition shipped() throws IOException {
        return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(SHIPPED)))
                .getOrThrow(err -> new AssertionError(err));
    }

    @Test
    void theShippedSpellIsCastableAndReadsItsReachFromTheJson() throws IOException {
        SpellDefinition revelio = shipped();

        assertEquals(SpellImplementationState.IMPLEMENTED, revelio.implementationState());
        Revelio.Settings settings = Revelio.Settings.of(revelio.range(), revelio.baseEffectDurationTicks());
        assertEquals(14.0, settings.radius());
        assertEquals(140, settings.durationTicks());
    }

    @Test
    void theShippedColourIsOpaqueLikeEveryOtherImplementedSpell() throws IOException {
        // Coming-soon stubs were authored as bare RGB (16777130). The outline services forgive alpha 0,
        // but anything else that reads Spell.getColor() as ARGB would draw nothing.
        int colour = shipped().color();
        assertEquals(0xFF, ARGB.alpha(colour));
        assertEquals(0xFFFFAA, colour & 0xFFFFFF);
    }

    @Test
    void anUnauthoredFieldFallsBackToTheDefaultRatherThanToNothing() {
        Revelio.Settings settings = Revelio.Settings.of(0.0f, 0);

        assertEquals(Revelio.DEFAULT_RADIUS, settings.radius());
        assertEquals(Revelio.DEFAULT_DURATION_TICKS, settings.durationTicks());
    }

    @Test
    void aTypoInThePackCannotTurnTheScanIntoALagSpike() {
        // The block scan is cubic in the radius: 200 would be two hundred times the work of 14.
        Revelio.Settings settings = Revelio.Settings.of(200.0f, 1_000_000);

        assertEquals(Revelio.MAX_RADIUS, settings.radius());
        assertEquals(Revelio.MAX_DURATION_TICKS, settings.durationTicks());
    }
}
