package at.koopro.wizardsandbeasts.apparition.licence;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The Ministry's {@code apparition_test} counter service: the Apparition Test sat in person, for the fee in the
 * Ministry's data. The board has already checked that the player stands at a Ministry clerk.
 *
 * <p>The examiner's judgement is {@link ApparitionLicence#evaluate}, unchanged — this adds the place and the price,
 * nothing about passing. The fee is taken only from a wizard the examiner would pass, so a refused attempt costs
 * nothing, and it is taken before the licence is written so a failed charge grants nothing.
 */
@NullMarked
public final class ApparitionTestService implements LocationService {

    private ApparitionTestService() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("apparition_test", new ApparitionTestService());
    }

    @Override
    public Quote quote(ServerPlayer player, Use use) {
        ApparitionLicence.Eligibility verdict = ApparitionLicence.evaluate(player);
        if (!verdict.eligible()) {
            return Quote.no(verdict.reason());
        }
        return Quote.yes(Component.translatable("apparition.wizards_and_beasts.test.quote",
                CurrencyHelper.formatFromKnuts(Use.billed() ? use.fee() : 0)));
    }

    @Override
    public boolean perform(ServerPlayer player, Use use) {
        ApparitionLicence.Eligibility verdict = ApparitionLicence.evaluate(player);
        if (!verdict.eligible()) {
            PlayerFeedback.refuse(player, Component.translatable("apparition.wizards_and_beasts.test.refused"),
                    verdict.reason());
            return false;
        }
        if (!use.charge(player, use.fee())) {
            return false;
        }
        ApparitionLicence.takeTest(player);
        PlayerFeedback.unlocked(player, Component.translatable("apparition.wizards_and_beasts.test.passed"),
                Component.translatable("apparition.wizards_and_beasts.test.passed.body"));
        return true;
    }
}
