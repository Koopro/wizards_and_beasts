package at.koopro.wizardsandbeasts.polyjuice;

import at.koopro.wizardsandbeasts.disguise.DisguiseSystemAPI;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.veritaserum.VeritaserumService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * The potion, and only what makes it a potion.
 *
 * <h2>What moved out</h2>
 * <p>The disguise itself — the state, the attachment, the sync, the countdown and the rendering — is
 * {@link DisguiseSystemAPI}, because none of it is about Polyjuice. What is left here is everything a
 * <em>dose</em> adds on top: it needs a sample, it lasts an hour, it refuses to stack, it cannot be
 * taken under Veritaserum, and it turns your stomach on the way through.
 *
 * <p>That split is what lets an operator command, and later a Metamorphmagus or a Boggart, wear a face
 * without inheriting a potion's rules — and it is why every rule below is a rule about drinking rather
 * than a rule about disguises.
 *
 * <h2>Presentation, never authority</h2>
 * <p>Unchanged and worth repeating at the caller: the drinker keeps their own UUID, so every permission
 * check, team, claim, vault balance and scoreboard entry still resolves to the real person. The
 * tempting next feature is always "and villagers should give you their discounts"; anything of that
 * shape has to be an explicit, enumerated exception, never a disguise made authoritative.
 */
@NullMarked
public final class PolyjuiceService {

    /** An hour in the fiction. Long enough to be a plan, short enough to be a risk. */
    public static final int DEFAULT_DURATION_TICKS = 20 * 300;

    /** The lurch as the body reshapes. Brief and non-negotiable — it is the tell. */
    private static final int TRANSFORM_NAUSEA_TICKS = 60;

    private PolyjuiceService() {}

    public static boolean isDisguised(ServerPlayer player) {
        return DisguiseSystemAPI.isDisguised(player);
    }

    /** Why a dose did or did not take. */
    public enum Result {
        TRANSFORMED,
        NO_SAMPLE,
        ALREADY_DISGUISED,
        /** Under Veritaserum. You cannot put a face on while you cannot hold one. */
        COMPELLED
    }

    /**
     * Drink a dose brewed with somebody's hair.
     *
     * <p>Refuses outright while already disguised rather than swapping face mid-effect. Two Polyjuice
     * doses stacking would mean a player could rotate through identities without ever appearing as
     * themselves, which removes the one window in which a disguise can be caught — and being caught
     * between faces is the entire tension of the thing. Note that this refuses against <em>any</em>
     * disguise, including one an operator set: a dose taken on top of an admin disguise would silently
     * put a five-minute clock on something that was meant not to have one.
     */
    public static Result drink(ServerPlayer player, Optional<UUID> targetId, String targetName,
                               int durationTicks) {
        if (targetId.isEmpty() || targetName.isBlank()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.no_sample"));
            return Result.NO_SAMPLE;
        }
        if (DisguiseSystemAPI.isDisguised(player)) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.already"));
            return Result.ALREADY_DISGUISED;
        }
        // Checked here rather than undone afterwards. A disguise granted and then reverted by the
        // Veritaserum tick would still have existed for up to ten ticks, which on a busy server is
        // long enough to walk through a door somebody is watching.
        if (VeritaserumService.blocksDisguise(player)) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.compelled"));
            return Result.COMPELLED;
        }

        DisguiseSystemAPI.apply(player, targetId.get(), targetName, durationTicks, true);
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, TRANSFORM_NAUSEA_TICKS,
                0, false, true, true));
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("polyjuice.wizards_and_beasts.became.title"),
                Component.translatable("polyjuice.wizards_and_beasts.became.body", targetName));
        return Result.TRANSFORMED;
    }

    /**
     * End a disguise now. Safe to call on somebody who is not wearing one.
     *
     * <p>Kept as a named entry point because {@code VeritaserumService} reverts a disguise as part of
     * enforcing the truth, and reads better calling the potion than the layer under it.
     */
    public static void revert(ServerPlayer player, boolean announce) {
        if (!DisguiseSystemAPI.clear(player, true)) {
            return;
        }
        if (announce) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("disguise.wizards_and_beasts.revert"));
        }
    }
}
