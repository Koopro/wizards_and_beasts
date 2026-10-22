package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.education.data.PlayerEducationData.ResultLine;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeCalculator;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.career.CareerPapers;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * A sitting at the examination desk, from the first page to the marks. Server only; the rules are {@link ExamRules}.
 *
 * <p>Practicals are credited at the seams where the work already happens — {@link CareerWork#record} (a cast that
 * counted as practice, a brew collected, a crop harvested, a beast fed, a wand finished), the Animagus transformation,
 * and a brew or herbal specimen handed in at the desk — so nothing is examined that the world does not already do.
 */
@NullMarked
public final class ExamSittings {

    /** Herbal specimens the Herbology examiner accepts at the desk. */
    public static final TagKey<Item> HERBOLOGY_SPECIMENS = ItemTags.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "exam_herbology_specimens"));

    /** Why a sitting could not open. */
    public enum Refusal {
        MODULE_OFF, NOT_AT_DESK, IN_PROGRESS, TOO_SOON, NO_SEAT;

        public String translationKey() {
            return "education.sitting.refused." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private ExamSittings() {}

    public static Optional<ExamSitting> active(ServerPlayer player) {
        return player.getData(ModAttachments.EDUCATION_DATA.get()).sitting();
    }

    /**
     * Opens a sitting at {@code level}.
     *
     * @param atDesk whether to insist on an examination desk within reach (false only for an operator's command)
     * @return why it did not open, or {@code null} when it did
     */
    public static @Nullable Refusal begin(ServerPlayer player, Qualification level, boolean atDesk) {
        if (!ModuleManager.isEnabled(Module.OWLS)) return Refusal.MODULE_OFF;
        if (atDesk && !RequestOWLExamPacket.atExaminationDesk(player)) return Refusal.NOT_AT_DESK;
        PlayerEducationData education = player.getData(ModAttachments.EDUCATION_DATA.get());
        if (education.sitting().isPresent()) return Refusal.IN_PROGRESS;
        PlayerOWLData record = player.getData(ModAttachments.OWL_DATA.get());
        long now = player.level().getGameTime();
        List<OWLSubject> papers = ExamRules.papers(level, record);
        if (level == Qualification.NEWT && (!record.examTaken() || papers.isEmpty())) return Refusal.NO_SEAT;
        if (!ExamRules.maySit(level, record, now)) return Refusal.TOO_SOON;

        Set<String> tomes = player.getData(ModAttachments.SKILL_DATA.get()).getLoreEntriesRead();
        ExamSitting sitting = new ExamSitting(level, now, now + ExamRules.SITTING_TICKS, papers, Set.of(),
                ExamQuestions.draw(papers, tomes, education.constellations(), new Random(player.getRandom().nextLong())),
                List.of());
        player.setData(ModAttachments.EDUCATION_DATA.get(), education.withSitting(Optional.of(sitting)));
        PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                Component.translatable("education.sitting.open.title", Component.translatable(level.translationKey())),
                Component.translatable("education.sitting.open.body", ExamRules.SITTING_TICKS / 1200L));
        EducationService.sync(player);
        return null;
    }

    /** Credits {@code subject}'s practical if the sitting still owes it. */
    public static boolean practical(ServerPlayer player, OWLSubject subject) {
        if (!ModuleManager.isEnabled(Module.OWLS)) return false;
        PlayerEducationData education = player.getData(ModAttachments.EDUCATION_DATA.get());
        ExamSitting sitting = education.sitting().orElse(null);
        if (sitting == null || !ExamRules.practicalOpen(sitting, subject, player.level().getGameTime())) return false;
        player.setData(ModAttachments.EDUCATION_DATA.get(),
                education.withSitting(Optional.of(sitting.withPractical(subject))));
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("education.practical.done.title", Component.translatable(subject.translationKey())),
                Component.translatable(ExamRules.practical(subject).translationKey()));
        EducationService.sync(player);
        return true;
    }

    /** The {@link CareerWork} seam: a piece of work done during a sitting is that subject's practical. */
    public static void onWork(ServerPlayer player, CareerWork.@Nullable Kind kind) {
        OWLSubject subject = ExamRules.subjectFor(kind);
        if (subject != null) {
            practical(player, subject);
        }
    }

    /** A brew or a herbal specimen handed in at the desk. Takes one and credits the practical, or takes nothing. */
    public static boolean handIn(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || active(player).isEmpty()) return false;
        // A ruined batch is not a potion: handing in the sludge of a failed brew used to pass the practical.
        boolean ruined = at.koopro.wizardsandbeasts.block.brew.WizardingCauldronBlock.RUINED_BREW_ID.equals(
                stack.get(at.koopro.wizardsandbeasts.registry.ModDataComponents.BREW_ID.get()));
        OWLSubject subject = stack.getItem() instanceof BrewItem ? (ruined ? null : OWLSubject.POTIONS)
                : stack.is(HERBOLOGY_SPECIMENS) ? OWLSubject.HERBOLOGY : null;
        if (subject == null || !practical(player, subject)) return false;
        stack.consume(1, player);
        return true;
    }

    /** Hands in the written paper: once, inside the clock, one answer per question (missing answers count wrong). */
    public static boolean answer(ServerPlayer player, List<Integer> chosen) {
        if (!ModuleManager.isEnabled(Module.OWLS)) return false;
        PlayerEducationData education = player.getData(ModAttachments.EDUCATION_DATA.get());
        ExamSitting sitting = education.sitting().orElse(null);
        if (sitting == null || sitting.answered() || sitting.questions().isEmpty()
                || player.level().getGameTime() >= sitting.deadlineTick()) {
            return false;
        }
        List<Integer> answers = new ArrayList<>(sitting.questions().size());
        for (int i = 0; i < sitting.questions().size(); i++) {
            answers.add(i < chosen.size() ? chosen.get(i) : -1);
        }
        player.setData(ModAttachments.EDUCATION_DATA.get(),
                education.withSitting(Optional.of(sitting.withAnswers(answers))));
        PlayerFeedback.toast(player, NoticeKind.SUCCESS, Component.translatable("education.paper.handed_in"));
        EducationService.sync(player);
        return true;
    }

    /**
     * Closes the sitting and marks it: each paper's coursework grade, lifted one step where the sitting earned it,
     * kept against the best on record.
     *
     * @return the marked papers; empty when no sitting was open
     */
    public static List<ResultLine> finish(ServerPlayer player) {
        PlayerEducationData education = player.getData(ModAttachments.EDUCATION_DATA.get());
        ExamSitting sitting = education.sitting().orElse(null);
        if (sitting == null) return List.of();
        Qualification level = sitting.level();

        Map<OWLSubject, OWLGrade> coursework = OWLGradeRules.gradeAll(level, OWLGradeCalculator.evidence(player));
        Set<OWLSubject> lifted = ExamRules.lifted(sitting);
        Map<OWLSubject, OWLGrade> fresh = ExamRules.mark(coursework, sitting.papers(), lifted);

        PlayerOWLData record = player.getData(ModAttachments.OWL_DATA.get());
        Map<OWLSubject, OWLGrade> merged = OWLGradeRules.best(level, record.gradesAt(level), fresh);
        PlayerOWLData next = record.afterSitting(level, merged, player.level().getGameTime());
        player.setData(ModAttachments.OWL_DATA.get(), next);

        List<ResultLine> results = new ArrayList<>();
        int passed = 0;
        int raised = 0;
        for (OWLSubject subject : sitting.papers()) {
            OWLGrade before = coursework.getOrDefault(subject, OWLGrade.T);
            OWLGrade after = fresh.getOrDefault(subject, OWLGrade.T);
            boolean up = after != before;
            results.add(new ResultLine(level, subject, before, after, up));
            if (after.passing) passed++;
            if (up) raised++;
        }
        player.setData(ModAttachments.EDUCATION_DATA.get(), education.marked(results));

        OWLExaminationHandler.syncToPlayer(player);
        CareerPapers.issue(player, next.grades(), player.getData(ModAttachments.PROFESSION_DATA.get()).profession());
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable("education.sitting.marked.title", Component.translatable(level.translationKey())),
                Component.translatable("education.sitting.marked.body", passed, results.size(), raised));
        EducationService.sync(player);
        at.koopro.wizardsandbeasts.owl.career.track.CareerProgression.sync(player);
        return results;
    }

    /** Called once a second per player: closes a sitting whose clock has run out. */
    public static void tick(ServerPlayer player) {
        ExamSitting sitting = active(player).orElse(null);
        if (sitting != null && ModuleManager.isEnabled(Module.OWLS)
                && player.level().getGameTime() >= sitting.deadlineTick()) {
            finish(player);
        }
    }
}
