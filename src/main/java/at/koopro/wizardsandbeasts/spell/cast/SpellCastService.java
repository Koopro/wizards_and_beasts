package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.SpellDeniedS2CPayload;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.SpellNetworkGuards;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler;
import at.koopro.wizardsandbeasts.spell.gamp.GampViolationEvent;
import at.koopro.wizardsandbeasts.spell.gamp.GampsLaw;
import at.koopro.wizardsandbeasts.wand.cast.WandCastingAllegianceSystem;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialCombatRules;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

public final class SpellCastService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FLAG_COLLAPSE_CAST_INSTABILITY_UNTIL = "obscurial_collapse_cast_instability_until_tick";

    /** GCD applied after every successful cast. Tuning constant. */
    public static final int GLOBAL_COOLDOWN_TICKS = 5;

    private SpellCastService() {}

    public static CastResult completeWandCastRelease(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            debugReject(player, SpellRejectCodes.NOT_SERVER_LEVEL);
            return CastResult.REJECTED;
        }

        if (!WandHelper.isHoldingWand(player)) {
            rejectWithHumanStress(player, SpellRejectCodes.NOT_HOLDING_WAND);
            return CastResult.REJECTED;
        }
        if (player.hasEffect(ModEffects.LANGLOCK)) {
            rejectWithHumanStress(player, SpellRejectCodes.LANGLOCKED);
            return CastResult.REJECTED;
        }
        float mental = player.getData(ModAttachments.MENTAL_STABILITY.get());
        if (mental <= 10f && serverLevel.random.nextFloat() < 0.20f) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.REQUIREMENTS_UNMET, "mental_misfire"));
            // REQUIREMENTS_UNMET is site-owned, so the client renders nothing for it and this is
            // the only line the player sees.
            player.displayClientMessage(Component.translatable("wandcraft.cast.reject.mental_misfire"), true);
            return CastResult.REJECTED;
        }

        var bondCheckStack = WandHelper.getWandStack(player);
        if (!WandHelper.isWandBondedTo(player, bondCheckStack)) {
            // Both codes map to their own lang key in SpellRejectCodes, so the client speaks and
            // this site does not. Same two sentences, now resolved in the player's language.
            if (WandComponents.getMaster(bondCheckStack).isEmpty()) {
                rejectWithHumanStress(player, SpellRejectCodes.WAND_NOT_BONDED);
            } else {
                rejectWithHumanStress(player, SpellRejectCodes.WAND_WRONG_MASTER);
            }
            return CastResult.REJECTED;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (!SpellNetworkGuards.canUseWand(player, data, "cast")) {
            applyHumanFailedCastStress(player);
            return CastResult.REJECTED;
        }
        String activeSpellId = data.getActiveSpellId();
        Spell spell = activeSpellId == null ? null : Spells.byId(activeSpellId);
        // Canonicalize: known-spell/cooldown/stat maps are keyed by the registered id, which for JSON
        // spells is namespaced. A bare id in the loadout slot (older saves, authored swap targets)
        // resolves to the same spell but would miss every keyed lookup below. Falls back to the raw id
        // when the spell doesn't resolve (the UNKNOWN_SPELL gate then fires with that raw id).
        String spellId = spell != null ? spell.getId() : activeSpellId;

        boolean obscurialDark = ObscurialRules.isDarkForm(player.getData(ModAttachments.HERITAGE_DATA.get()));

        // Cooldown clock invariant: ALWAYS use getGameTime() (monotonic, shared across every dimension
        // via DerivedLevelData — getDayTime()/fixed_time do NOT affect it). Cooldowns are stored as
        // absolute expiry ticks and persist across relog/death/dimension, so the stamp here and every
        // reader (isOnCooldown, the HUD) must read the same clock. Never substitute getDayTime() or a
        // System-millis clock, and never reset/re-stamp an active cooldown.
        long currentTick = serverLevel.getGameTime();
        // Deterministic reject precedence (no-active-spell -> global cooldown) is decided purely in
        // SpellCastGate; the switch reproduces each gate's exact player feedback. The bond and
        // canUseWand guards above, and the random misfires below, stay inline (side-effecting/ordered).
        SpellCastGate gate = SpellCastGate.evaluate(new SpellCastGate.Inputs(
                activeSpellId != null,
                spell != null,
                spell != null && data.knowsSpell(spellId),
                spell != null && ObscurialRules.isObscurialAbility(spell),
                spell == null || !Config.enforceSpellRequirements || spell.getRequirement().isMet(player, data),
                spell != null && ObscurialRules.isDarkFormOnlySpell(spell) && !obscurialDark,
                spell != null && obscurialDark && !ObscurialRules.isSpellAllowedInDarkForm(spell),
                data.isOnCooldown(spellId, currentTick),
                data.isGlobalCooldownActive(currentTick)));
        if (gate != null) {
            switch (gate) {
                case NO_ACTIVE_SPELL ->
                        rejectWithHumanStress(player, SpellRejectCodes.NO_ACTIVE_SPELL);
                case UNKNOWN_SPELL ->
                        rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.UNKNOWN_SPELL, activeSpellId));
                case SPELL_NOT_KNOWN ->
                        rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.SPELL_NOT_KNOWN, spellId));
                case OBSCURIAL_ABILITY_INPUT ->
                        rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.ABILITY_REQUIRES_ABILITY_INPUT, spellId));
                case REQUIREMENTS_UNMET -> {
                    rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.REQUIREMENTS_UNMET, spellId));
                    // Site-owned: the requirement describes itself, which beats any fixed sentence
                    // a lang key could hold ("Requires Stupefy at Adept", not "requirement unmet").
                    player.displayClientMessage(
                            Component.empty().withStyle(ChatFormatting.RED)
                                    .append(spell.getRequirement().describe()),
                            true);
                }
                case OBSCURIAL_DARK_ONLY ->
                        rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_DARK_ONLY_OUTSIDE_FORM, spellId));
                case OBSCURIAL_DARK_RESTRICTED -> {
                    ObscurialCombatRules.applyBlockedCastStressSpike(player);
                    ObscurialCombatRules.applyBlockedCastPressureBacklash(player);
                    debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_DARK_RESTRICTED, spellId));
                }
                case ON_COOLDOWN ->
                        rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.COOLDOWN_ACTIVE, spellId));
                case GLOBAL_COOLDOWN ->
                        rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.COOLDOWN_ACTIVE, "global_cooldown"));
            }
            return CastResult.REJECTED;
        }

        var wandStack = WandHelper.getWandStack(player);
        WandStats wandStats = WandStatsResolver.resolve(wandStack, player.registryAccess());
        CastContext castContext = CastContext.create(
                player,
                wandStack,
                spell,
                spell instanceof JsonSpell jsonSpell ? jsonSpell.definition() : null,
                wandStats,
                spell.getProficiency(player));
        castContext = castContext.withAllegiance(WandCastingAllegianceSystem.resolve(wandStack));
        castContext = castContext.withCompatibility(WandCastingAllegianceSystem.applyLayer(castContext, serverLevel));
        Identifier spellKey;
        try {
            spellKey = Identifier.parse(spell.getId());
        } catch (Exception ignored) {
            spellKey = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, spell.getId());
        }
        castContext = castContext.withScalingProfile(ProficiencyScaler.getProfileForPlayer(player, spellKey));

        long collapseInstabilityUntil = parseLong(
                player.getData(ModAttachments.HERITAGE_DATA.get()).getFlag(FLAG_COLLAPSE_CAST_INSTABILITY_UNTIL), 0L);
        if (serverLevel.getGameTime() < collapseInstabilityUntil) {
            if (serverLevel.random.nextFloat() < ObscurialCombatRules.getCollapseCastFizzleChance()) {
                float backlash = ObscurialCombatRules.getCollapseCastBacklashDamage();
                if (backlash > 0f) {
                    player.hurt(serverLevel.damageSources().magic(), backlash);
                }
                debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.COLLAPSE_INSTABILITY_FIZZLE, spellId));
                return CastResult.REJECTED;
            }
        }

        float instabilityChance = ObscurialRules.getInstabilityFizzleChance(player, serverLevel);
        if (instabilityChance > 0f && serverLevel.random.nextFloat() < instabilityChance) {
            debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_INSTABILITY_FIZZLE, spellId));
            float backlash = ObscurialRules.getInstabilityBacklashDamage();
            if (backlash > 0f) {
                player.hurt(serverLevel.damageSources().magic(), backlash);
            }
            ObscurialCombatRules.consumeCastSpike(player);
            return CastResult.REJECTED;
        }

        GampsLaw.Violation violation = GampsLaw.validate(castContext);
        if (violation != null) {
            GampViolationEvent event = new GampViolationEvent(player, violation, castContext);
            if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
                if (violation.isHardReject()) {
                    player.displayClientMessage(violation.loreMessage().copy().withStyle(ChatFormatting.GOLD), true);
                    DebugHooks.logSpellCast(player, "cast_gamp_reject", violation.domain().name());
                    if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
                        // Site-owned: the violation lore line was shown immediately above.
                        SpellDeniedS2CPayload.sendTo(player, SpellRejectCodes.GAMP_HARD_REJECT);
                    }
                    return CastResult.GAMP_REJECTED;
                }
                player.displayClientMessage(
                        violation.loreMessage().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC),
                        true);
                castContext = castContext.withGampPenalty(violation.domain());
                DebugHooks.logSpellCast(player, "cast_gamp_penalty", violation.domain().name());
            }
        }

        try {
            SpellExecutor.executeGeneric(castContext, serverLevel);
            DebugHooks.logSpellCast(player, "cast_success", spellId);
        } catch (Exception ex) {
            LOGGER.error("Spell cast failed for player '{}' spell '{}'", player.getName().getString(), spellId, ex);
            DebugHooks.logSpellCast(player, "cast_exception", spellId);
            // A thrown cast is the one refusal that used to be completely silent — no packet, no
            // sound, no counter — so a mod defect was indistinguishable from a dropped keypress.
            // It reports like any other reject now, minus the stress penalty: the player did nothing
            // wrong, and no cooldown is stamped either, so the attempt costs them nothing.
            debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.CAST_FAILED, spellId));
            return CastResult.REJECTED;
        }

        // Presentation only, and deliberately outside the try: the animation describes a cast that
        // has already resolved, and a failure to describe it is not a failed cast. A spell with no
        // declared timing broadcasts nothing at all.
        if (castContext.definition() != null) {
            at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload
                    .broadcast(player, spellId, castContext.definition());
        }

        // Composed and clamped in ModifierStack; the proficiency channel is already in there. This
        // used to multiply the scaling profile in again on top of a stack that had already had the
        // Proficiency enum tier pushed into it, so practice was paid twice.
        float cooldownMult = castContext.modifiers().finalCooldown();
        int cooldown = SpellCastGate.resolveCooldownTicks(spell.getBaseCooldownTicks(), cooldownMult);
        long expiryTick = currentTick + cooldown;
        data.setCooldown(spellId, expiryTick);
        data.incrementCastCount(spellId);
        // The Unforgivables are the corrupting acts in the lore; until now only dark artefacts stained the
        // caster. No-op for every other spell.
        at.koopro.wizardsandbeasts.corruption.UnforgivableToll.onCast(player, spellId);
        // The Trace: the Ministry registers illegal magic the instant it is worked.
        at.koopro.wizardsandbeasts.ministry.law.MagicalOffence offence =
                at.koopro.wizardsandbeasts.ministry.law.MagicalOffence.forSpell(spellId);
        if (offence != null) {
            at.koopro.wizardsandbeasts.ministry.law.TraceService.report(player, offence);
        }
        int newCount = data.getCastCount(spellId);
        long gcdEndTick = currentTick + GLOBAL_COOLDOWN_TICKS;
        data.setGlobalCooldownEndTick(gcdEndTick);
        ObscurialCombatRules.consumeCastSpike(player);

        SpellDataDeltaS2CPayload.sendTo(player, spellId, expiryTick, newCount, data.getSuccessfulHits(spellId), gcdEndTick);
        return CastResult.SUCCESS;
    }

    private static void debugReject(ServerPlayer player, String reason) {
        player.getData(ModAttachments.SPELL_DATA.get()).incrementRejectReason(reason);
        DebugHooks.logSpellCast(player, "cast_reject", reason);
        if (Config.debugLogSpellGateReasons) {
            LOGGER.debug("SpellCast rejected for '{}' reason={}", player.getName().getString(), reason);
        }
        // Central player feedback for every reject routed through here. The server sends the *code*
        // and nothing else; the client resolves it to a lang key, chooses the channel (the spell HUD
        // while it is up, the action bar otherwise) and plays the denied sound. Codes whose text is
        // composed at the site — requirements, Gamp — resolve to null on the client, so the richer
        // sentence those sites already wrote is never doubled by a generic one.
        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            SpellDeniedS2CPayload.sendTo(player, reason);
        }
    }

    private static void rejectWithHumanStress(ServerPlayer player, String reason) {
        applyHumanFailedCastStress(player);
        debugReject(player, reason);
    }

    private static void applyHumanFailedCastStress(ServerPlayer player) {
        var typeData = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(typeData)) return;
        if (ObscurialRules.isDarkForm(typeData)) return;
        ObscurialCombatRules.applyHumanFailedCastStressSpike(player);
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
