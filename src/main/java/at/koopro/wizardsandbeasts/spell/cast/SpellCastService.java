package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.SpellDataDeltaS2CPacket;
import at.koopro.wizardsandbeasts.network.SpellNetworkGuards;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.JsonSpell;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.SpellExecutor;
import at.koopro.wizardsandbeasts.spell.Spells;
import at.koopro.wizardsandbeasts.wand.cast.WandAllegianceSystem;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import at.koopro.wizardsandbeasts.util.WandHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class SpellCastService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FLAG_COLLAPSE_CAST_INSTABILITY_UNTIL = "obscurial_collapse_cast_instability_until_tick";

    private SpellCastService() {}

    public static void completeWandCastRelease(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            debugReject(player, SpellRejectCodes.NOT_SERVER_LEVEL);
            return;
        }

        if (!WandHelper.isHoldingWand(player)) {
            rejectWithHumanStress(player, SpellRejectCodes.NOT_HOLDING_WAND);
            return;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (!SpellNetworkGuards.canUseWand(player, data, "cast")) {
            applyHumanFailedCastStress(player);
            return;
        }
        String spellId = data.getActiveSpellId();
        if (spellId == null) {
            rejectWithHumanStress(player, SpellRejectCodes.NO_ACTIVE_SPELL);
            return;
        }

        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.UNKNOWN_SPELL, spellId));
            return;
        }

        if (!data.knowsSpell(spellId)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.SPELL_NOT_KNOWN, spellId));
            return;
        }
        if (ObscurialRules.isObscurialAbility(spell)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.ABILITY_REQUIRES_ABILITY_INPUT, spellId));
            player.displayClientMessage(Component.literal("\u00A75Use Obscurial ability keys (N/M) while in obscurus form."), true);
            return;
        }

        if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(data)) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.REQUIREMENTS_UNMET, spellId));
            player.displayClientMessage(
                    Component.literal("\u00A7c" + spell.getRequirement().getDescription()),
                    true);
            return;
        }

        boolean obscurialDark = ObscurialRules.isDarkForm(player.getData(ModAttachments.TYPE_DATA.get()));
        if (ObscurialRules.isDarkFormOnlySpell(spell) && !obscurialDark) {
            rejectWithHumanStress(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_DARK_ONLY_OUTSIDE_FORM, spellId));
            player.displayClientMessage(Component.literal("\u00A75This obscurus ability can only be cast in dark form."), true);
            return;
        }

        if (obscurialDark && !ObscurialRules.isSpellAllowedInDarkForm(spell)) {
            ObscurialRules.applyBlockedCastStressSpike(player);
            ObscurialRules.applyBlockedCastPressureBacklash(player);
            debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_DARK_RESTRICTED, spellId));
            player.displayClientMessage(
                    Component.literal("\u00A75Obscurus rejects that spell and lashes back."),
                    true);
            return;
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
            return;
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
        castContext = castContext.withAllegiance(WandAllegianceSystem.resolve(wandStack));
        castContext = castContext.withCompatibility(WandAllegianceSystem.applyLayer(castContext, serverLevel));

        long collapseInstabilityUntil = parseLong(
                player.getData(ModAttachments.TYPE_DATA.get()).getFlag(FLAG_COLLAPSE_CAST_INSTABILITY_UNTIL), 0L);
        if (serverLevel.getGameTime() < collapseInstabilityUntil) {
            if (serverLevel.random.nextFloat() < ObscurialRules.getCollapseCastFizzleChance()) {
                float backlash = ObscurialRules.getCollapseCastBacklashDamage();
                if (backlash > 0f) {
                    player.hurt(serverLevel.damageSources().magic(), backlash);
                }
                debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.COLLAPSE_INSTABILITY_FIZZLE, spellId));
                player.displayClientMessage(Component.literal("\u00A75Residual obscurus instability disrupts your spell."), true);
                return;
            }
        }

        float instabilityChance = ObscurialRules.getInstabilityFizzleChance(player, serverLevel);
        if (instabilityChance > 0f && serverLevel.random.nextFloat() < instabilityChance) {
            debugReject(player, SpellRejectCodes.withDetail(SpellRejectCodes.OBSCURIAL_INSTABILITY_FIZZLE, spellId));
            float backlash = ObscurialRules.getInstabilityBacklashDamage();
            if (backlash > 0f) {
                player.hurt(serverLevel.damageSources().magic(), backlash);
            }
            ObscurialRules.consumeCastSpike(player);
            player.displayClientMessage(Component.literal("\u00A74Your obscurus destabilizes the cast and backlashes."), true);
            return;
        }

        try {
            SpellExecutor.executeGeneric(castContext, serverLevel);
            DebugHooks.logSpellCast(player, "cast_success", spellId);
        } catch (Exception ex) {
            LOGGER.error("Spell cast failed for player '{}' spell '{}'", player.getName().getString(), spellId, ex);
            DebugHooks.logSpellCast(player, "cast_exception", spellId);
            return;
        }

        float cooldownMult = castContext.modifiers().finalCooldown();
        int cooldown = Math.max(1, (int)(spell.getBaseCooldownTicks() * cooldownMult));
        long expiryTick = currentTick + cooldown;
        data.setCooldown(spellId, expiryTick);
        data.incrementCastCount(spellId);
        int newCount = data.getCastCount(spellId);
        ObscurialRules.consumeCastSpike(player);

        SpellDataDeltaS2CPacket.sendTo(player, spellId, expiryTick, newCount, data.getSuccessfulHits(spellId));
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
        var typeData = player.getData(ModAttachments.TYPE_DATA.get());
        if (!ObscurialRules.isObscurial(typeData)) return;
        if (ObscurialRules.isDarkForm(typeData)) return;
        ObscurialRules.applyHumanFailedCastStressSpike(player);
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
