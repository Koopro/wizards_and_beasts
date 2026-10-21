package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The view the sheet and desk draw: it crosses the wire intact, and its "next" lines point the right way. */
class EducationViewTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<String> keys(List<Component> lines) {
        return lines.stream().map(c -> ((TranslatableContents) c.getContents()).getKey()).toList();
    }

    @Test
    void theViewCrossesTheWireIntactAndNeverCarriesAnAnswer() {
        EducationView.SubjectLine charms = new EducationView.SubjectLine(OWLSubject.CHARMS, 9, new int[] {3, 9, 16},
                OWLGrade.E, OWLGrade.A, false, 9, new int[] {16, 22, 28}, OWLGrade.D, null, 2,
                ExamRules.Practical.CHARM_CAST.translationKey());
        EducationView.SittingView sitting = new EducationView.SittingView(Qualification.OWL, 4_000L,
                List.of(new EducationView.PaperLine(OWLSubject.CHARMS, "k", false, true)),
                List.of(new EducationView.QuestionLine(OWLSubject.HISTORY_OF_MAGIC, Component.translatable("q"),
                        List.of(Component.translatable("a"), Component.translatable("b")))), false);
        EducationView view = new EducationView(true, 1, 0, 9_000L, 0L, false, true, true, List.of(charms), sitting,
                List.of(new EducationView.ResultView(Qualification.OWL, OWLSubject.CHARMS, OWLGrade.A, OWLGrade.E, true)),
                List.of(Component.translatable("education.next.first_newt", 1)));

        ByteBuf buf = Unpooled.buffer();
        EducationView.STREAM_CODEC.encode(buf, view);
        EducationView back = EducationView.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "bytes left over");
        assertEquals(1, back.owlSittings());
        assertTrue(back.maySitNewt() && back.careerOpen() && !back.maySitOwl());
        EducationView.SubjectLine line = back.subjects().getFirst();
        assertEquals(OWLGrade.E, line.projected());
        assertEquals(OWLGrade.A, line.owlRecord());
        assertNull(line.newtRecord());
        assertEquals(28, line.newt()[2]);
        assertEquals(2, line.lessons());
        assertNotNull(back.sitting());
        assertEquals(4_000L, back.sitting().deadline());
        assertEquals(2, back.sitting().questions().getFirst().options().size());
        assertTrue(back.lastResults().getFirst().lifted());
        assertEquals(List.of("education.next.first_newt"), keys(back.next()));
    }

    @Test
    void aNewCandidateIsToldToSitTheirOwls() {
        List<String> next = keys(EducationService.nextSteps(PlayerOWLData.DEFAULT, Map.of(), null, false, null, 0L, false));
        assertEquals("education.next.first_owl", next.getFirst());
        assertFalse(next.contains("education.next.career"), "careers before any exam");
    }

    @Test
    void aCandidateWithASeatIsPointedAtTheNewtAndTheNearestCareer() {
        Map<OWLSubject, OWLGrade> owl = new EnumMap<>(OWLSubject.class);
        owl.put(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E);
        owl.put(OWLSubject.CHARMS, OWLGrade.E);
        owl.put(OWLSubject.POTIONS, OWLGrade.A);
        PlayerOWLData record = new PlayerOWLData(owl, true, 1, 100L);
        List<String> next = keys(EducationService.nextSteps(record, owl, Profession.JOURNALIST, false, null,
                100L + ExamRules.RESIT_INTERVAL_TICKS, true));
        assertTrue(next.contains("education.next.first_newt"), next.toString());
        assertTrue(next.contains("education.next.seat"), "Potions A is one step from a seat: " + next);
        assertTrue(next.size() <= EducationService.MAX_NEXT);
    }

    @Test
    void anOpenSittingComesFirst() {
        ExamSitting sitting = new ExamSitting(Qualification.OWL, 0L, ExamRules.SITTING_TICKS, List.of(OWLSubject.CHARMS),
                Set.of(), List.of(), List.of());
        List<String> next = keys(EducationService.nextSteps(PlayerOWLData.DEFAULT, Map.of(), null, false, sitting, 1L,
                false));
        assertEquals("education.next.sitting", next.getFirst());
    }

    @Test
    void aResitIsSuggestedOnlyWhenTheWorkWouldMarkHigher() {
        Map<OWLSubject, OWLGrade> held = Map.of(OWLSubject.CHARMS, OWLGrade.A);
        PlayerOWLData record = new PlayerOWLData(held, true, 1, 100L);
        long later = 100L + ExamRules.RESIT_INTERVAL_TICKS;
        assertFalse(keys(EducationService.nextSteps(record, held, null, false, null, later, false))
                .contains("education.next.resit_owl"));
        assertTrue(keys(EducationService.nextSteps(record, Map.of(OWLSubject.CHARMS, OWLGrade.E), null, false, null,
                later, false)).contains("education.next.resit_owl"));
        assertTrue(keys(EducationService.nextSteps(record, Map.of(OWLSubject.CHARMS, OWLGrade.E), null, false, null,
                200L, false)).contains("education.next.resit_owl_later"));
    }
}
