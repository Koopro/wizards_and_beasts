package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
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
            player.displayClientMessage(Component.literal("\u00A75Your mind falters; the spell misfires."), true);
            return CastResult.REJECTED;
        }

        var bondCheckStack = WandHelper.getWandStack(player);
        if (!WandHelper.isWandBondedTo(player, bondCheckStack)) {
            if (WandComponents.getMaster(bondCheckStack).isEmpty()) {
                rejectWithHumanStress(player, SpellRejectCodes.WAND_NOT_BONDED);
                player.displayClientMessage(Component.translatable("wandcraft.cast.requires_bond"), true);
            } else {
                rejectWithHumanStress(player, SpellRejectCodes.WAND_WRONG_MASTER);
                player.displayClientMessage(Component.translatable("wandcraft.cast.wrong_master"), true);
            }
            return CastResult.REJECTED;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (!SpellNetworkGuards.canUseWand(player, data, "cast")) {
            applyHumanFailedCastStress(player);
            return CastResult.REJECTED;
        }
        String spellId = data.getActiveSpellId();
        if (spellId == null) {
            rejectWithHumanStress(player, SpellRejectCodes.NO_ACTIVE_SPELL);
            return CastResult.REJECTED;
        }

        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.UNKNOWN_SPELL, spellId));
            return CastResult.REJECTED;
        }

        if (!data.knowsSpell(spellId)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.SPELL_NOT_KNOWN, spellId));
            return CastResult.REJECTED;
        }
        if (ObscurialRules.isObscurialAbility(spell)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.ABILITY_REQUIRES_ABILITY_INPUT, spellId));
            player.displayClientMessage(Component.literal("\u00A75Use Obscurial ability keys (N/M) while in obscurus form."), true);
            return CastResult.REJECTED;
        }

        if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(player, data)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.REQUIREMENTS_UNMET, spellId));
            player.displayClientMessage(
                    Component.literal("\u00A7c" + spell.getRequirement().getDescription()),
                    true);
            return CastResult.REJECTED;
        }

        boolean obscurialDark = ObscurialRules.isDarkForm(player.getData(ModAttachments.HERITAGE_DATA.get()));
        if (ObscurialRules.isDarkFormOnlySpell(spell) && !obscurialDark) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_DARK_ONLY_OUTSIDE_FORM, spellId));
            player.displayClientMessage(Component.literal("\u00A75This obscurus ability can only be cast in dark form."), true);
            return CastResult.REJECTED;
        }

        if (obscurialDark && !ObscurialRules.isSpellAllowedInDarkForm(spell)) {
            ObscurialCombatRules.applyBlockedCastStressSpike(player);
            ObscurialCombatRules.applyBlockedCastPressureBacklash(player);
            debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_DARK_RESTRICTED, spellId));
            player.displayClientMessage(
                    Component.literal("\u00A75Obscurus rejects that spell and lashes back."),
                    true);
            return CastResult.REJECTED;
        }

        long currentTick = serverLevel.getGameTime();
        if (data.isOnCooldown(spellId, currentTick)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.COOLDOWN_ACTIVE, spellId));
            float remainingSec = (data.getCooldownExpiry(spellId) - currentTick) / 20f;
            player.displayClientMessage(
                    Component.literal("")
                            .append(Component.literal(spell.getDisplayName()).withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" recharging ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(String.format("%.1fs", Math.max(0f, remainingSec)))
                                    .withStyle(ChatFormatting.RED)),
                    true);
            return CastResult.REJECTED;
        }

        var wandStack = WandHelper.getWandStack(player);
        WandStats wandStats = WandStatsResolver.resolve(wandStack);
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
                player.displayClientMessage(Component.literal("\u00A75Residual obscurus instability disrupts your spell."), true);
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
            player.displayClientMessage(Component.literal("\u00A74Your obscurus destabilizes the cast and backlashes."), true);
            return CastResult.REJECTED;
        }

        GampsLaw.Violation violation = GampsLaw.validate(castContext);
        if (violation != null) {
            GampViolationEvent event = new GampViolationEvent(player, violation, castContext);
            if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
                if (violation.isHardReject()) {
                    player.displayClientMessage(violation.loreMessage().copy().withStyle(ChatFormatting.GOLD), true);
                    DebugHooks.logSpellCast(player, "cast_gamp_reject", violation.domain().name());
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
            return CastResult.REJECTED;
        }

        float cooldownMult = castContext.modifiers().finalCooldown();
        cooldownMult *= castContext.scalingProfile().cooldownMult();
        int baseCooldown = spell.getBaseCooldownTicks();
        int cooldown = Math.max(1, Math.round(baseCooldown * cooldownMult));
        int reductionFloor = Math.max(1, Math.round(baseCooldown * 0.5f));
        cooldown = Math.max(cooldown, reductionFloor);
        long expiryTick = currentTick + cooldown;
        data.setCooldown(spellId, expiryTick);
        data.incrementCastCount(spellId);
        int newCount = data.getCastCount(spellId);
        ObscurialCombatRules.consumeCastSpike(player);

        SpellDataDeltaS2CPayload.sendTo(player, spellId, expiryTick, newCount, data.getSuccessfulHits(spellId));
        return CastResult.SUCCESS;
    }

    private static void debugReject(ServerPlayer player, String reason) {
        player.getData(ModAttachments.SPELL_DATA.get()).incrementRejectReason(reason);
        DebugHooks.logSpellCast(player, "cast_reject", reason);
        if (Config.debugLogSpellGateReasons) {
            LOGGER.debug("SpellCast rejected for '{}' reason={}", player.getName().getString(), reason);
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
