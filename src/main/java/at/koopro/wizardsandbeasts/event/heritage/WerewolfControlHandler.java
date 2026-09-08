package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.werewolf.FeralController;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfAttributes;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfTransformService;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The werewolf half of loss of control: the tick that drives the body, and the lifecycle around it.
 *
 * <p><b>The constraint wall is not here any more.</b> No item use, no block or entity interaction, no
 * breaking, no inventory, no dropping and no casting are all enforced by
 * {@link at.koopro.wizardsandbeasts.form.constraint.FormConstraintEvents}, shared with the Animagus
 * layer; this class only declares which set applies, through
 * {@code WerewolfRules.constraintsFor}. See {@code documentation/TRANSFORMED_PLAYER_CONTRACT.md}.
 *
 * <p>What is left here is what is specific to a werewolf rather than to being transformed: driving the
 * body ({@link FeralController}), banking rage from damage, and putting the wolf's attributes back after
 * a relog or a respawn.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class WerewolfControlHandler {

    private WerewolfControlHandler() {}

    // ── the drive ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (!WerewolfState.isLossOfControl(data)) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
                continue;
            }
            // Switching heritage off must not trap anyone inside a body they cannot leave — the same
            // rule AnimagusTransformService.revertIfModuleDisabled follows. Control comes back; the
            // shape is left alone.
            if (!ModuleManager.isEnabled(Module.HERITAGE)) {
                WerewolfTransformService.setLossOfControl(player, data, false);
                HeritageAPI.syncTransformation(player);
                continue;
            }

            // Mid-change the body is held still by WerewolfTransformService.tickPending. The constraints
            // above still apply -- that is the whole reason the flag is set at the onset -- but driving a
            // body that is being remade would fight the freeze.
            if (WerewolfState.hasPendingTransform(data)) {
                continue;
            }
            FeralController.of(player).tick(player, level);
        }
    }

    /**
     * Being hurt is what winds a wolf up fastest.
     *
     * <p>{@code Post} rather than {@code Pre} deliberately: the rage should track what actually landed
     * after armour and resistances, not what was aimed. A wolf in full enchanted plate is not enraged by
     * a punch that did nothing.
     */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FeralController controller = FeralController.peek(player);
        if (controller != null) {
            controller.onDamaged(event.getNewDamage());
        }
    }

    // ── lifecycle ──────────────────────────────────────────────────────

    /**
     * Death ends the night. The heritage attachment is {@code copyOnDeath}, so without this a player
     * would respawn still flagged feral, with a controller driving a body that had already lost its
     * wolf's attributes.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && WerewolfRules.isWerewolf(player)) {
            WerewolfTransformService.forceReset(player, WerewolfState.data(player));
        }
    }

    /**
     * Re-applies the wolf's body after a relog or a respawn.
     *
     * <p>The form, the transformation state and the loss-of-control flag are all persisted, but the
     * five attribute modifiers are transient by design ({@code addTransientModifier}, as everywhere else
     * in this mod), so a werewolf who logs out mid-night would otherwise come back a wolf with a human's
     * health and reach.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        restoreWolfBody(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        restoreWolfBody(event.getEntity());
    }

    private static void restoreWolfBody(net.minecraft.world.entity.player.Player raw) {
        if (!(raw instanceof ServerPlayer player)) {
            return;
        }
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (WerewolfRules.isWerewolf(data) && WerewolfRules.isTransformed(data)) {
            WerewolfAttributes.apply(player);
        }
    }

}
