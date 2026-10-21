package at.koopro.wizardsandbeasts.owl.career;

import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A career's working day: the first time in an in-game day a career holder does their career's work,
 * the day's wage goes into their Gringotts vault (see {@link CareerProfile}).
 *
 * <p>Each hook site calls {@link #record} with the kind of work that just happened; this class decides
 * whether it is this player's work and whether today is already paid. Paid per day, not per act, so
 * no amount of repetition is worth more than doing the work once — the same shape as the daily
 * practice budget ({@link SpellPractice#DAILY_PRACTICE}).
 *
 * <p>Off with {@link Module#OWLS} (no careers) or {@link Module#GRINGOTTS} (no vault to pay into).
 */
@NullMarked
public final class CareerWork {

    /** The kinds of work a career is paid for. Each has exactly one kind of hook site. */
    public enum Kind {
        /** A Combat or Defence spell that counted as practice. */
        DUELLING,
        /** A Utility spell that counted as practice. */
        CHARMWORK,
        /** A finished brew collected from a cauldron. */
        BREWING,
        /** A mature crop harvested. */
        HARVEST,
        /** A bond creature fed, or a bestiary page taken deeper. */
        CREATURE_CARE,
        /** A wand finished at the bench. */
        WANDCRAFT,
        /** A spellbook copied at the Study Lectern for someone else to learn from. */
        TEACHING,
        /** Time in the air on a broom. */
        FLYING,
        /** A parcel sent by owl post. */
        CORRESPONDENCE
    }

    private CareerWork() {}

    /** The work a practised spell counts as, or {@code null} for Dark Arts, which no career pays for. */
    public static @Nullable Kind forSpell(@Nullable Spell spell) {
        if (spell == null) return null;
        SpellCategory category = spell.getCategory();
        if (category == SpellCategory.UTILITY) return Kind.CHARMWORK;
        if (category == SpellCategory.COMBAT || category == SpellCategory.DEFENSE) return Kind.DUELLING;
        return null;
    }

    /** Whether this work, done today, earns a wage for this career. Pure. */
    public static boolean pays(@Nullable Profession career, Kind kind, long today, long lastPaidDay) {
        if (career == null || !career.offered()) return false;
        CareerProfile profile = CareerProfile.of(career);
        return profile.work() == kind && profile.dailyWage() > 0 && today > lastPaidDay;
    }

    /**
     * Records that {@code player} just did {@code kind} of work.
     *
     * @return whether a wage was paid
     */
    public static boolean record(ServerPlayer player, @Nullable Kind kind) {
        // The same work, done during a sitting at the examination desk, is that subject's practical.
        at.koopro.wizardsandbeasts.education.ExamSittings.onWork(player, kind);
        if (kind == null
                || !ModuleManager.isEnabled(Module.OWLS)
                || !ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return false;
        }
        PlayerProfessionData data = player.getData(ModAttachments.PROFESSION_DATA.get());
        long today = SpellPractice.day(player.level().getGameTime());
        if (!pays(data.profession(), kind, today, data.lastPaidDay())) {
            return false;
        }
        CareerProfile profile = CareerProfile.of(data.profession());
        // A career track's rank scales the day's pay; a career without a track is paid exactly as before.
        long wage = Math.round(profile.dailyWage() * (double)
                at.koopro.wizardsandbeasts.owl.career.track.CareerProgression.wageMultiplier(player, data.profession()));
        // Paid in the largest coins, as Gringotts would, so a wage reads as Galleons in the vault.
        GringottsBilling.credit(player, MoneyFlow.CAREER_WAGE, wage);
        player.setData(ModAttachments.PROFESSION_DATA.get(), data.paidOn(today));
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("owls.career.payday.title"),
                Component.translatable("owls.career.payday.body",
                        Component.translatable(data.profession().translationKey()),
                        CurrencyHelper.formatFromKnuts(wage)));
        return true;
    }
}
