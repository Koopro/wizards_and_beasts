package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.standing.MagicalStanding;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Everything the law now saves survives a save, and every save written before it still loads. */
class LawSaveCompatTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    private static <T> T roundTrip(Codec<T> codec, T value) {
        return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow();
    }

    private static <T> T parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void theRecordKeepsSuspensionsAndConductAndOldRecordsHaveNeither() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT
                .withSuspension(LicenseType.APPARITION, 5000L)
                .withConductAdjusted(7)
                .withOffence(MagicalOffence.STATUTE_OF_SECRECY_BREACH, 5.0f);
        assertEquals(record, roundTrip(PlayerMinistryRecord.CODEC, record));

        PlayerMinistryRecord old = parse(PlayerMinistryRecord.CODEC, "{\"notoriety\":12.0}");
        assertTrue(old.suspensions().isEmpty());
        assertEquals(0, old.conduct());
    }

    @Test
    void suspensionsNeverShortenAndLiftCleanly() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT.withSuspension(LicenseType.BROOM, 9000L)
                .withSuspension(LicenseType.BROOM, 100L);
        assertTrue(record.suspendedAt(LicenseType.BROOM, 8999L));
        assertFalse(record.suspendedAt(LicenseType.BROOM, 9000L));
        assertFalse(record.withoutSuspension(LicenseType.BROOM).suspendedAt(LicenseType.BROOM, 0L));
        assertTrue(record.withoutLapsedSuspensions(9000L).suspensions().isEmpty());
    }

    @Test
    void anOverturnedConvictionComesOffTheFileWithItsHeat() {
        PlayerMinistryRecord convicted = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.STATUTE_OF_SECRECY_BREACH, 5.0f)
                .withOffence(MagicalOffence.STATUTE_OF_SECRECY_BREACH, 5.75f);
        PlayerMinistryRecord cleared = convicted.withOffenceWithdrawn(MagicalOffence.STATUTE_OF_SECRECY_BREACH, 5.75f);
        assertEquals(1, cleared.offenceCount(MagicalOffence.STATUTE_OF_SECRECY_BREACH));
        assertEquals(5.0f, cleared.notoriety(), 1.0e-4f);
        assertEquals(0, cleared.withOffenceWithdrawn(MagicalOffence.STATUTE_OF_SECRECY_BREACH, 5.0f)
                .offenceCount(MagicalOffence.STATUTE_OF_SECRECY_BREACH));
        assertEquals(PlayerMinistryRecord.DEFAULT,
                PlayerMinistryRecord.DEFAULT.withOffenceWithdrawn(MagicalOffence.CRUCIO, 30.0f),
                "withdrawing what was never filed changes nothing");
    }

    @Test
    void conductIsBounded() {
        assertEquals(PlayerMinistryRecord.MAX_CONDUCT,
                PlayerMinistryRecord.DEFAULT.withConductAdjusted(10_000).conduct());
        assertEquals(-PlayerMinistryRecord.MAX_CONDUCT,
                PlayerMinistryRecord.DEFAULT.withConductAdjusted(-10_000).conduct());
    }

    @Test
    void evidenceIncidentsAndCasesRoundTripAndOldOnesLoad() {
        UUID source = UUID.randomUUID();
        Evidence evidence = Evidence.from(EvidenceKind.WIZARD_TESTIMONY, 4L, source, "Harry", 99L);
        assertEquals(evidence, roundTrip(Evidence.CODEC, evidence));

        Incident incident = new Incident(4L, source, "Harry", "lumos", OVERWORLD, BlockPos.ZERO, 1L,
                LegalClass.DARK, Exposure.LOW, false, false, 0, List.of(), Optional.empty(), true, true);
        assertEquals(incident, roundTrip(Incident.CODEC, incident));

        OpenCase open = new OpenCase(CaseStage.AURORS_ASSIGNED, List.of(4L), 1L, 0L, OVERWORLD, BlockPos.ZERO, 50L, 3,
                true, 17L, true, Optional.of(source), 900L);
        assertEquals(open, roundTrip(OpenCase.CODEC, open));
        assertEquals(Optional.of(source), open.aurorAt(899L));
        assertEquals(Optional.empty(), open.aurorAt(900L), "an Auror's hold lapses");

        OpenCase old = parse(OpenCase.CODEC, "{\"stage\":\"summoned\",\"stage_since\":5,"
                + "\"last_known_dimension\":\"minecraft:overworld\",\"last_known_pos\":[1,2,3]}");
        assertEquals(0L, old.caseId());
        assertFalse(old.cold());
        assertTrue(old.auror().isEmpty());

        UnsolvedIncident unsolved = new UnsolvedIncident(4L, 700L, Optional.of(source), 300L);
        assertEquals(unsolved, roundTrip(UnsolvedIncident.CODEC, unsolved));

        NpcCase posted = new NpcCase(8L, Identifier.fromNamespaceAndPath("wizards_and_beasts", "dark_curse"),
                "sectumsempra", LegalClass.DARK, OVERWORLD, new BlockPos(1, 2, 3), true, new BlockPos(30, 2, 3),
                10L, 20L, Optional.of(source), 15L, Optional.of(UUID.randomUUID()), true);
        assertEquals(posted, roundTrip(NpcCase.CODEC, posted));
    }

    @Test
    void standingKeepsPublicAndProfessionalReputationAndOldStandingLoadsNeutral() {
        MagicalStanding standing = new MagicalStanding(3.0f, 4.0f, -12.0f, 8.0f, "auror");
        assertEquals(standing, roundTrip(MagicalStanding.CODEC, standing));
        MagicalStanding old = parse(MagicalStanding.CODEC, "{\"tradition\":2.0}");
        assertEquals(0.0f, old.publicRep());
        assertEquals(0.0f, old.professionFor("auror"));
        assertTrue(new MagicalStanding(0.0f, 0.0f).isNeutral());
        assertFalse(standing.isNeutral());
        assertTrue(Map.of().isEmpty());
    }
}
