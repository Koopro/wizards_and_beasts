package at.koopro.wizardsandbeasts.travel.profile;

import at.koopro.wizardsandbeasts.apparition.ApparitionPointService;
import at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile.ActionKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * What a Travel tab button does, on the server. Each action goes through the same door its command or counter uses
 * and is checked from scratch there: the client named a kind and an argument, nothing more.
 */
@NullMarked
public final class TravelActions {

    /** Toll steps a keeper cycles through: free, then one, two and three Sickles. */
    static final int TOLL_STEP = CurrencyHelper.KNUTS_PER_SICKLE;

    private TravelActions() {}

    /** Runs {@code kind} for {@code player}. Unknown or empty actions do nothing. */
    public static void perform(ServerPlayer player, ActionKind kind, String arg) {
        switch (kind) {
            case MARK_HERE -> {
                if (apparitionOn()) {
                    tell(player, ApparitionPointService.mark(player, TravelProfiles.markName(player)));
                }
            }
            case FORGET_POINT -> {
                if (apparitionOn()) {
                    tell(player, ApparitionPointService.forget(player, arg));
                }
            }
            case SIT_TEST -> {
                ApparitionLicence.Eligibility verdict = ApparitionLicence.takeTestAway(player);
                if (verdict.eligible()) {
                    PlayerFeedback.unlocked(player, Component.translatable("apparition.wizards_and_beasts.test.passed"),
                            Component.translatable("apparition.wizards_and_beasts.test.passed.body"));
                } else {
                    PlayerFeedback.refuse(player, Component.translatable("apparition.wizards_and_beasts.test.refused"),
                            verdict.reason());
                }
            }
            case CYCLE_TOLL -> cycleToll(player, arg);
            case NONE -> { }
        }
    }

    /**
     * Steps the toll on one of the player's own public hearths: free → 1 → 2 → 3 Sickles → free. Only the keeper:
     * an unowned hearth has nobody to pay, and an administrator who wants to change someone's toll has the command.
     */
    private static void cycleToll(ServerPlayer player, String address) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(level);
        FlooRegistryEntry entry = manager.getEntry(address);
        if (entry == null || !entry.isOwnedBy(player.getUUID()) || !entry.isPublic()) {
            return;
        }
        int next = nextToll(entry.tollKnuts());
        manager.setToll(address, next);
        PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.toll.set",
                entry.networkAddress(), next == 0
                        ? Component.translatable("floo.wizards_and_beasts.toll.free")
                        : Component.literal(CurrencyHelper.formatFromKnuts(next))));
    }

    /** The toll after {@code current} in the cycle. */
    public static int nextToll(int current) {
        int next = (current / TOLL_STEP + 1) * TOLL_STEP;
        return next > FlooRegistryEntry.MAX_TOLL_KNUTS ? 0 : next;
    }

    private static boolean apparitionOn() {
        return ModuleManager.isEnabled(Module.PLAYER_ABILITIES) && ModuleManager.isEnabled(Module.APPARITION);
    }

    private static void tell(ServerPlayer player, ApparitionPointService.Outcome outcome) {
        PlayerFeedback.actionBar(player, outcome.message());
    }
}
