package at.koopro.wizardsandbeasts.heritage.identity;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * A people's own arts, practised, as their road to professions ({@link PracticeKind}, {@link HeritagePracticeRules}).
 *
 * <p>Called by the hook where the art is used — the house-elf's snap, a goblin's appraisal — after it succeeded. Only
 * the people the art belongs to is counted; the server is the only writer.
 */
@NullMarked
public final class HeritagePractice {

    private HeritagePractice() {}

    public static HeritagePracticeData of(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_PRACTICE.get());
    }

    /**
     * Counts one use of {@code kind} by {@code player}.
     *
     * @return profession points it paid (0 most of the time)
     */
    public static int record(ServerPlayer player, PracticeKind kind) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)
                || HeritageAPI.getData(player).getSelectedHeritage() != kind.heritage()) {
            return 0;
        }
        long day = SpellPractice.day(player.level().getGameTime());
        HeritagePracticeRules.Outcome outcome = HeritagePracticeRules.practise(of(player), kind, day);
        player.setData(ModAttachments.HERITAGE_PRACTICE.get(), outcome.data());
        if (outcome.points() > 0) {
            ProfessionSystemAPI.awardPoints(player, outcome.points());
            PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                    Component.translatable("heritage.wizards_and_beasts.practice.point"),
                    Component.translatable("heritage.wizards_and_beasts.practice.point.body",
                            Component.translatable(kind.translationKey())));
        }
        return outcome.points();
    }
}
