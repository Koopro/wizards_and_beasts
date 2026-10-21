package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Lessons with a keeper: the NPC half of preparing for the O.W.L.s.
 *
 * <p>Each examined subject is a service {@code lesson_<subject>}; a location's data decides which it teaches, from
 * which standing tier and for what fee (the apothecary teaches Potions, Herbology and Care of Magical Creatures;
 * Ollivanders Ancient Runes and Charms; the Ministry Defence and History of Magic). A lesson is one a day, of any
 * subject, and counts toward that subject's O.W.L. coursework — three lessons are worth its Acceptable, never
 * more ({@link OWLGradeRules#lessonCredit}). Lessons prepare a candidate; only their own work earns the higher
 * grades, and N.E.W.T.s never count lessons at all.
 */
@NullMarked
public final class LessonServices {

    private static final String KEY = "education.lesson.";

    private LessonServices() {}

    public static void register(LocationServices.Sink sink) {
        for (OWLSubject subject : OWLSubject.values()) {
            if (OWLGradeRules.teachable(subject)) {
                sink.register(serviceName(subject), new Lesson(subject));
            }
        }
    }

    public static String serviceName(OWLSubject subject) {
        return "lesson_" + subject.name().toLowerCase(Locale.ROOT);
    }

    /** Why a lesson cannot be had now, as a translation-key suffix; {@code null} when it can. Pure. */
    public static @org.jspecify.annotations.Nullable String refusal(PlayerEducationData data, OWLSubject subject,
                                                                    long today) {
        if (data.lessonsIn(subject) >= OWLGradeRules.LESSONS_TO_A) return "taught_out";
        if (data.lastLessonDay() >= today) return "one_a_day";
        return null;
    }

    static final class Lesson implements LocationService {
        private final OWLSubject subject;

        Lesson(OWLSubject subject) {
            this.subject = subject;
        }

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            if (!ModuleManager.isEnabled(Module.OWLS)) {
                return Quote.no(Component.translatable(KEY + "no_exams"));
            }
            PlayerEducationData data = player.getData(ModAttachments.EDUCATION_DATA.get());
            String why = refusal(data, subject, SpellPractice.day(player.level().getGameTime()));
            Component name = Component.translatable(subject.translationKey());
            if (why != null) {
                return Quote.no(Component.translatable(KEY + why, name));
            }
            return Quote.yes(Component.translatable(KEY + "quote", name,
                    CurrencyHelper.formatFromKnuts(Use.billed() ? use.fee() : 0),
                    data.lessonsIn(subject), OWLGradeRules.LESSONS_TO_A));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            if (!ModuleManager.isEnabled(Module.OWLS)) {
                return false;
            }
            PlayerEducationData data = player.getData(ModAttachments.EDUCATION_DATA.get());
            long today = SpellPractice.day(player.level().getGameTime());
            if (refusal(data, subject, today) != null || !use.charge(player, use.fee())) {
                return false;
            }
            PlayerEducationData next = data.withLesson(subject, today);
            player.setData(ModAttachments.EDUCATION_DATA.get(), next);
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable(KEY + "done.title", Component.translatable(subject.translationKey())),
                    Component.translatable(KEY + "done.body", next.lessonsIn(subject), OWLGradeRules.LESSONS_TO_A));
            EducationService.sync(player);
            return true;
        }
    }
}
