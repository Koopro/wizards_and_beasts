package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Server-side gameplay logic for the Animagus ability: toggling between the
 * wizard's human form and their chosen beast form, and choosing which beast.
 * <p>
 * Transformation reuses the server-authoritative form system
 * ({@link TransitionManager} → {@link FormSystemAPI}); this service only
 * gates the action on Animagus state and tracks {@code currentlyTransformed}.
 */
public final class AnimagusTransformService {

    public static final String ABILITY_ID = "animagus";

    private AnimagusTransformService() {}

    /** True if the player has the Animagus skill ability (capability to perform the ritual). */
    public static boolean hasAnimagusSkill(ServerPlayer player) {
        return SkillSystemAPI.hasAbility(player, ABILITY_ID);
    }

    /** True if the player has completed the ritual and may transform. */
    public static boolean canTransform(ServerPlayer player) {
        return ModuleManager.isEnabled(Module.PLAYER_ABILITIES)
                && PlayerAbilityHelper.isAnimagusUnlocked(player)
                && PlayerAbilityHelper.getAnimagusFormId(player) != null;
    }

    /**
     * Toggles the player between beast form and their default (human/heritage) form.
     * Provides action-bar feedback for every rejection.
     */
    public static void toggleTransform(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            // Disabling the module must not trap anyone mid-form: reverting is still allowed, only
            // transforming is blocked. Returning unconditionally here soft-locked a beast into its body.
            revertIfModuleDisabled(player);
            return;
        }
        if (TransitionManager.isTransitioning(player.getUUID())) {
            return;
        }

        boolean transformed = PlayerAbilityHelper.isCurrentlyTransformed(player);
        if (transformed) {
            revert(player);
            return;
        }

        if (!PlayerAbilityHelper.isAnimagusUnlocked(player)) {
            feedback(player, "You have not completed the Animagus transformation.", ChatFormatting.GRAY);
            return;
        }
        String formId = PlayerAbilityHelper.getAnimagusFormId(player);
        if (!AnimagusForms.isAnimagusForm(formId)) {
            feedback(player, "You have no Animagus form. Choose one with /wandb animagus form <beast>.", ChatFormatting.GRAY);
            return;
        }

        boolean started = TransitionManager.startTransition(player, formId);
        if (!started) {
            feedback(player, "You cannot transform right now.", ChatFormatting.RED);
            return;
        }
        PlayerAbilityHelper.setCurrentlyTransformed(player, true);
        // Transforming without being on the Animagus Registry is an offence — paperwork, not a manhunt.
        if (!PlayerAbilityHelper.isAnimagusRegistered(player)) {
            at.koopro.wizardsandbeasts.ministry.law.TraceService.report(
                    player, at.koopro.wizardsandbeasts.ministry.law.MagicalOffence.UNREGISTERED_ANIMAGUS);
        }
    }

    /**
     * Drops the player out of beast form because the module went away, keeping their chosen
     * {@code animagusFormId} so re-enabling the module restores them intact. Returns true if a
     * revert happened. Safe to call every tick — it is a no-op unless the module is off <em>and</em>
     * the player is transformed.
     */
    public static boolean revertIfModuleDisabled(ServerPlayer player) {
        if (ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return false;
        }
        if (!PlayerAbilityHelper.isCurrentlyTransformed(player)) {
            return false;
        }
        revert(player);
        return true;
    }

    /** Reverts the player to their default form. Safe to call when not transformed. */
    public static void revert(ServerPlayer player) {
        if (!PlayerAbilityHelper.isCurrentlyTransformed(player)) {
            return;
        }
        PlayerAbilityHelper.setCurrentlyTransformed(player, false);
        AnimagusAbilityService.clearPassives(player);
        if (!TransitionManager.isTransitioning(player.getUUID())) {
            FormSystemAPI.resetToDefault(player);
        }
    }

    /**
     * Forces the player out of beast form without animation — used on death and
     * other hard state resets where a transition is inappropriate.
     */
    public static void forceRevert(ServerPlayer player) {
        if (PlayerAbilityHelper.isCurrentlyTransformed(player)) {
            PlayerAbilityHelper.setCurrentlyTransformed(player, false);
        }
        AnimagusAbilityService.clearPassives(player);
    }

    /**
     * Sets the player's chosen beast form. Rejected while transformed (you must be
     * human to register a different form).
     *
     * @param beastOrId either a beast key ({@code "stag"}) or full id ({@code "animagus_stag"})
     * @return the resolved form id, or null if invalid / rejected
     */
    @Nullable
    public static String setForm(ServerPlayer player, String beastOrId) {
        if (PlayerAbilityHelper.isCurrentlyTransformed(player)) {
            feedback(player, "Revert to human form before changing your Animagus form.", ChatFormatting.RED);
            return null;
        }
        String resolved = AnimagusForms.resolve(beastOrId);
        if (resolved == null) {
            feedback(player, "Unknown Animagus form: " + beastOrId, ChatFormatting.RED);
            return null;
        }
        PlayerAbilityHelper.setAnimagusFormId(player, resolved);
        return resolved;
    }

    private static void feedback(ServerPlayer player, String msg, ChatFormatting color) {
        player.displayClientMessage(Component.literal(msg).withStyle(color), true);
    }
}
