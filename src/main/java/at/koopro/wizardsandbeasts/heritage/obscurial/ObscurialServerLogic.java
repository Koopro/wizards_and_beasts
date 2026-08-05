package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NullMarked;

/**
 * Server-side entry points for the Obscurial player abilities — form toggle, stress vent, and the two
 * combat abilities (Surge / Grasp). The logic was lifted verbatim out of the C2S payload handlers when
 * those abilities moved onto the ability wheel; the wheel behaviors are thin adapters over these methods,
 * exactly as Apparition/Legilimency/Animagus are over theirs.
 *
 * <p>The {@code can*} predicates expose the <i>permission</i> half of each rule for the ability grant layer
 * (wheel visibility). Transient state — transitions, lockouts, drain, cooldowns — is deliberately not part
 * of them: each entry point re-checks everything itself, with its own player feedback.
 */
@NullMarked
public final class ObscurialServerLogic {

    private ObscurialServerLogic() {}

    // ── permission predicates (grant layer) ──

    private static PlayerHeritageData heritageData(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_DATA.get());
    }

    /** True if the player is an Obscurial at all — the standing requirement for the form toggle. */
    public static boolean canToggleForm(ServerPlayer player) {
        return heritageData(player).getSelectedHeritage() == Heritage.OBSCURIAL;
    }

    /** Stress venting is a human-form action; it is rejected outright while in obscurus form. */
    public static boolean canStressVent(ServerPlayer player) {
        PlayerHeritageData data = heritageData(player);
        return data.getSelectedHeritage() == Heritage.OBSCURIAL && !ObscurialRules.isDarkForm(data);
    }

    /** Obscurial combat abilities require obscurus form. */
    public static boolean canUseAbilities(ServerPlayer player) {
        PlayerHeritageData data = heritageData(player);
        return ObscurialRules.isObscurial(data) && ObscurialRules.isDarkForm(data);
    }

    // ── entry points ──

    /** Toggles between the human and obscurus forms. Authority for the lockout and drain gates. */
    public static void toggleForm(ServerPlayer player) {
        PlayerHeritageData data = heritageData(player);
        if (data.getSelectedHeritage() != Heritage.OBSCURIAL) {
            return;
        }
        if (TransitionManager.isTransitioning(player.getUUID())) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (ObscurialResourceManager.isTransformLockedOut(player, gameTime)) {
            long remainSec = Math.max(1L, (ObscurialResourceManager.getLockoutUntilTick(player) - gameTime) / 20L);
            player.displayClientMessage(Component.literal("§4Obscurus exhausted. You can re-form in " + remainSec + "s."), true);
            return;
        }

        String current = data.getActiveFormId();
        String target = "obscurial_dark".equals(current) ? "obscurial_human" : "obscurial_dark";
        if ("obscurial_dark".equals(target) && ObscurialResourceManager.getDrain(player) <= 5.0f) {
            player.displayClientMessage(Component.literal("§4Not enough control to safely summon obscurus form."), true);
            return;
        }
        boolean started = TransitionManager.startTransition(player, target);
        if (!started) {
            player.displayClientMessage(Component.literal("§cTransformation failed."), true);
        }
    }

    /** True while the player is in obscurus form — the toggle's on-state, owned here, not by the framework. */
    public static boolean isDarkForm(ServerPlayer player) {
        return ObscurialRules.isDarkForm(heritageData(player));
    }

    /** Vents accumulated obscurus strain for a drawback. Authority for the cooldown gate. */
    public static void stressVent(ServerPlayer player) {
        PlayerHeritageData data = heritageData(player);
        if (data.getSelectedHeritage() != Heritage.OBSCURIAL) return;
        if (ObscurialRules.isDarkForm(data)) return;

        long now = player.level().getGameTime();
        long cooldownUntil = ObscurialResourceManager.getStressVentCooldownUntil(player);
        if (now < cooldownUntil) {
            long remain = Math.max(1L, (cooldownUntil - now) / 20L);
            player.displayClientMessage(Component.literal("§5Stress Vent recovering: " + remain + "s"), true);
            return;
        }

        ObscurialResourceManager.reduceStress(player, ObscurialResourceManager.getStressVentRecovery());
        ObscurialResourceManager.setStressVentCooldownUntil(player, now + ObscurialResourceManager.getStressVentCooldownTicks());
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                ObscurialResourceManager.getStressVentDrawbackTicks(), 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                ObscurialResourceManager.getStressVentDrawbackTicks(), 0, false, true, true));
        player.displayClientMessage(Component.literal("§dYou vent obscurus strain, but feel drained."), true);
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
    }

    /** Casts one Obscurial combat ability. Authority for the form, cooldown and requirement gates. */
    public static void useAbility(ServerPlayer player, String abilityId) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var typeData = heritageData(player);
        PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());

        if (!ObscurialRules.isObscurial(typeData) || !ObscurialRules.isDarkForm(typeData)) {
            spellData.incrementRejectReason(SpellRejectCodes.ABILITY_NOT_IN_DARK_FORM);
            player.displayClientMessage(Component.literal("§5Obscurial abilities require obscurus form."), true);
            return;
        }

        String safeAbility = PacketCodecUtils.normalizeIdentifier(abilityId);
        ObscurialAbility ability = ObscurialAbility.bySpellId(safeAbility);
        if (ability == null) {
            spellData.incrementRejectReason(SpellRejectCodes.ABILITY_UNKNOWN);
            player.displayClientMessage(Component.literal("§cUnknown Obscurial ability."), true);
            return;
        }

        Spell spell = Spells.byId(ability.spellId());
        if (spell == null || !ObscurialRules.isObscurialAbility(spell)) {
            spellData.incrementRejectReason(SpellRejectCodes.ABILITY_SPELL_MISSING);
            player.displayClientMessage(Component.literal("§cAbility backend is unavailable."), true);
            return;
        }

        long now = level.getGameTime();
        if (spellData.isOnCooldown(spell.getId(), now)) {
            spellData.incrementRejectReason(SpellRejectCodes.ABILITY_COOLDOWN_ACTIVE);
            float sec = (spellData.getCooldownExpiry(spell.getId()) - now) / 20f;
            player.displayClientMessage(Component.literal(String.format("§e%s recharging %.1fs", ability.displayName(), Math.max(0f, sec))), true);
            return;
        }
        if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(player, spellData)) {
            spellData.incrementRejectReason(SpellRejectCodes.ABILITY_REQUIREMENTS_UNMET);
            player.displayClientMessage(Component.literal("§c").append(spell.getRequirement().describe()), true);
            return;
        }

        try {
            spell.execute(level, player, player.getMainHandItem());
        } catch (Exception ex) {
            spellData.incrementRejectReason(SpellRejectCodes.ABILITY_EXECUTE_FAILED);
            player.displayClientMessage(Component.literal("§cObscurial ability failed to execute."), true);
            return;
        }

        int cooldown = Math.max(1, (int) (spell.getBaseCooldownTicks() * ObscurialCombatRules.getAbilityCooldownMultiplier(player)));
        long expiryTick = now + cooldown;
        int oldCount = spellData.getCastCount(spell.getId());
        spellData.setCooldown(spell.getId(), expiryTick);
        spellData.incrementCastCount(spell.getId());
        SpellDataDeltaS2CPayload.sendTo(
                player,
                spell.getId(),
                expiryTick,
                oldCount + 1,
                spellData.getSuccessfulHits(spell.getId()),
                spellData.getGlobalCooldownEndTick());
        if (ObscurialRules.getStressTier(ObscurialResourceManager.getStress(player)) == ObscurialRules.StressTier.VOLATILE) {
            player.displayClientMessage(Component.literal("§5Volatile stress is prolonging your ability cooldowns."), true);
        }
    }
}
