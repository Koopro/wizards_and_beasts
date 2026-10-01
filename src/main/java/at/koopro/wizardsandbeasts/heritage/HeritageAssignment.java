package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The three ways a player's heritage is decided, each written once: a player choosing at the onboarding gate,
 * an administrator assigning one, an administrator sending a player back to the gate.
 *
 * <p>{@link HeritageAPI#commit} / {@link HeritageAPI#clear} already own the body, the roll and the sync; what
 * each caller still wrote out for itself was the part around them — the profession points and which event to
 * post. Posting the wrong one is not cosmetic: {@code PlayerHeritageSelectedEvent} is what awards the three
 * starting skill points, so an administrator's assignment must post {@code Changed}, never {@code Selected}, or
 * every reassignment would pay out again. The gate packet, the {@code /wandb player heritage} commands and the
 * Control Center all go through here.
 *
 * <p>Nothing here checks permission or selectability — callers do, before calling. Server thread only.
 */
@NullMarked
public final class HeritageAssignment {

    /** Profession points a newly committed character starts with. */
    public static final int STARTING_PROFESSION_POINTS = 3;

    private HeritageAssignment() {}

    /**
     * The pairing is valid: {@code variant} is a lineage of {@code heritage}.
     *
     * @return null when valid, otherwise a short reason
     */
    public static @Nullable String checkPairing(@Nullable Heritage heritage, @Nullable HeritageVariant variant) {
        if (heritage == null) {
            return "unknown_heritage";
        }
        if (variant == null || variant.getParentHeritage() != heritage) {
            return "invalid_variant";
        }
        return null;
    }

    /**
     * A player's own choice at the onboarding gate. The condition, if any, is applied after the commit: the roll
     * and the lock belong to the lineage, and the condition is something that happened to the character the commit
     * created.
     */
    public static void select(ServerPlayer player, Heritage heritage, HeritageVariant variant,
                              @Nullable ConditionOrigin condition) {
        startProfession(player);
        HeritageAPI.commit(player, heritage, variant);
        if (condition != null) {
            HeritageAPI.afflict(player, condition);
        }
        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageSelectedEvent(player, heritage, variant));
    }

    /** An administrator sets a player's heritage. Posts {@code Changed}: no starting skill points are re-awarded. */
    public static void assign(ServerPlayer target, Heritage heritage, HeritageVariant variant) {
        startProfession(target);
        HeritageAPI.commit(target, heritage, variant);
        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageChangedEvent(target, heritage, variant));
    }

    /**
     * An administrator sends one player back to the onboarding gate: the heritage is taken off all the way and the
     * selection screen is reopened on their client. Trained stats and skills survive ({@link HeritageAPI#clear}).
     */
    public static void resetOnboarding(ServerPlayer target) {
        HeritageAPI.clear(target, true);
        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageResetEvent(target));
    }

    private static void startProfession(ServerPlayer player) {
        PlayerHeritageData data = HeritageAPI.getData(player);
        data.resetProfessionProgress();
        data.addProfessionPoints(STARTING_PROFESSION_POINTS);
    }
}
