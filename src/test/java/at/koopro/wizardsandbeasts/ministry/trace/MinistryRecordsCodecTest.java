package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Case files and the age on a record survive a save, and a save written before them still loads. */
class MinistryRecordsCodecTest {

    private static <T> T roundTrip(Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void anIncidentKeepsEveryFact() {
        Incident incident = new Incident(42L, UUID.randomUUID(), "Harry", "wizards_and_beasts:lumos",
                Identifier.withDefaultNamespace("overworld"), new BlockPos(1, 2, 3), 1234L, LegalClass.UNFORGIVABLE,
                Exposure.EXTREME, true, true, 3, List.of(UUID.randomUUID()), Optional.of(MagicalOffence.CRUCIO),
                true);
        assertEquals(incident, roundTrip(Incident.CODEC, incident));
    }

    @Test
    void aDossierWithAnOpenCaseAndWaitingLettersSurvives() {
        OpenCase open = new OpenCase(CaseStage.AURORS_ASSIGNED, List.of(1L, 2L), 10L, 0L,
                Identifier.withDefaultNamespace("the_nether"), new BlockPos(-5, 70, 9), 2200L, 2, true);
        Dossier dossier = new Dossier(1, 2, Optional.of(open), List.of(new CaseNotice("summons", "", 24000L)),
                Optional.of(Verdict.WARNING));
        assertEquals(dossier, roundTrip(Dossier.CODEC, dossier));
        assertEquals(Dossier.EMPTY, roundTrip(Dossier.CODEC, Dossier.EMPTY));
    }

    @Test
    void aRecordSavedBeforeAgesExistedLoadsOfAgeWithItsFileIntact() {
        JsonElement legacy = JsonParser.parseString(
                "{\"notoriety\": 12.5, \"offences\": {\"crucio\": 2}, \"outstandingFineKnuts\": 30}");
        PlayerMinistryRecord record = PlayerMinistryRecord.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(WizardingAge.UNSET, record.ageYears());
        assertFalse(WizardingAge.isUnderage(record.ageAt(1_000_000L, 24000L)));
        assertEquals(2, record.offenceCount(MagicalOffence.CRUCIO));
        assertFalse(record.wandConfiscatedAt(0L));
    }

    @Test
    void ageAndConfiscationSurviveAndAPardonKeepsTheAge() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT.withAge(13, 500L).withWandConfiscatedUntil(9000L);
        PlayerMinistryRecord back = roundTrip(PlayerMinistryRecord.CODEC, record);
        assertEquals(record, back);
        assertTrue(back.wandConfiscatedAt(8999L));
        assertFalse(back.wandConfiscatedAt(9000L));
        assertEquals(13, back.pardoned().ageYears(), "a pardon is not a birth certificate");
    }

    @Test
    void aChildComesOfAgeAtSeventeenWithWorldTime() {
        long year = 8 * 24000L;
        assertEquals(15, WizardingAge.yearsAt(15, 0L, year - 1, year));
        assertEquals(16, WizardingAge.yearsAt(15, 0L, year, year));
        assertTrue(WizardingAge.isUnderage(WizardingAge.yearsAt(15, 0L, 2 * year - 1, year)));
        assertFalse(WizardingAge.isUnderage(WizardingAge.yearsAt(15, 0L, 2 * year, year)));
        assertFalse(WizardingAge.isUnderage(WizardingAge.UNSET), "no birth record is of age");
        assertEquals(15, WizardingAge.yearsAt(15, 1000L, 0L, year), "time before the record never makes anyone younger");
    }
}
