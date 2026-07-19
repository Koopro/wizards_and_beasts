package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import at.koopro.wizardsandbeasts.spell.effect.EffectCadence;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectEntry;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side held-beam spells while {@link WandItem} is in use.
 * Reach ramps with tick count to stay aligned with the client beam extension
 * ({@link BeamRayResolver#extensionBlocksPerTick()}).
 */
public final class WandBeamChannelLogic {

    private static final Map<UUID, WandBeamSession> SESSIONS = new ConcurrentHashMap<>();

    private WandBeamChannelLogic() {}

    public static void tick(ServerPlayer player, ItemStack wandStack) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(wandStack.getItem() instanceof WandItem)) {
            endChannel(player);
            return;
        }
        if (!WandHelper.isWandBondedTo(player, wandStack)) {
            endChannel(player);
            return;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        String spellId = data.getActiveSpellId();
        if (spellId == null) {
            endChannel(player);
            return;
        }

        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            endChannel(player);
            return;
        }

        SpellProperties props = spell.getProperties();
        if (props == null) {
            endChannel(player);
            return;
        }

        CastType ct = props.getCastType();
        if (ct != CastType.BEAM_LETHAL && ct != CastType.BEAM_CHANNEL) {
            endChannel(player);
            return;
        }
        if (!WandBeamSpellIds.isHeldChannelSpell(spell.getId())) {
            endChannel(player);
            return;
        }

        if (!data.knowsSpell(spellId)) {
            endChannel(player);
            return;
        }
        long currentTick = level.getGameTime();
        if (data.isOnCooldown(spellId, currentTick)) {
            endChannel(player);
            return;
        }
        if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(player, data)) {
            endChannel(player);
            return;
        }

        float range = props.getRange() * WandStatsResolver.resolve(wandStack).rangeFor(spell);
        if (range <= 0) range = 32f;

        WandBeamSession s = SESSIONS.computeIfAbsent(player.getUUID(), u -> new WandBeamSession());
        if (!Objects.equals(s.spellId, spellId)) {
            runChannelEndEffects(player, s);
            WandBeamSpellHandlers.clearSessionEffects(player, s);
        }
        s.syncSpell(spellId);
        s.beamTicks++;

        int targetScanInterval = WandBeamSpellHandlers.getTargetScanIntervalTicks();
        int channelEffectInterval = WandBeamSpellHandlers.getChannelEffectIntervalTicks();

        float maxReach = Math.min(range, s.beamTicks * BeamRayResolver.extensionBlocksPerTick());
        boolean leviosa = WandBeamSpellIds.isLeviosa(spell.getId());
        boolean aguamenti = WandBeamSpellIds.isAguamenti(spell.getId());
        LivingEntity target = null;
        Entity leviosaTarget = null;
        if (leviosa) {
            if (s.cachedTarget != null) {
                leviosaTarget = WandBeamSpellHandlers.findEntityInLevel(player, s.cachedTarget);
                if (!WandBeamSpellHandlers.isValidLeviosaTarget(player, leviosaTarget)) {
                    leviosaTarget = null;
                }
            }
            if (leviosaTarget == null || s.beamTicks % targetScanInterval == 0) {
                Entity scanned = WandBeamSpellHandlers.findLeviosaTargetAlongCrosshair(
                        player, maxReach, leviosaTarget == null);
                if (scanned != null) {
                    leviosaTarget = scanned;
                }
            }
            s.cachedTarget = leviosaTarget == null ? null : leviosaTarget.getUUID();
        } else if (!aguamenti) {
            if (s.beamTicks % targetScanInterval == 0 || s.cachedTarget == null) {
                BeamRay ray = BeamRayResolver.resolve(player, 1.0f, maxReach, BeamRayResolver.LIVING_FILTER);
                if (ray.hitsEntity()
                        && ray.hit() instanceof net.minecraft.world.phys.EntityHitResult ehr
                        && ehr.getEntity() instanceof LivingEntity living) {
                    target = living;
                }
                s.cachedTarget = target == null ? null : target.getUUID();
            } else {
                target = WandBeamSpellHandlers.findLivingInLevel(player, s.cachedTarget);
            }
        }

        if (ct == CastType.BEAM_CHANNEL) {
            runChannelEffects(level, player, spell, s, target, channelEffectInterval);
        }

        if (leviosa) {
            WandBeamSpellHandlers.handleLeviosaChannel(player, spell.getId(), leviosaTarget, s, maxReach);
        } else if (aguamenti) {
            WandBeamSpellHandlers.handleAguamentiChannel(level, player, spell, s, maxReach);
        } else if (ct == CastType.BEAM_LETHAL) {
            WandBeamSpellHandlers.handleAvada(level, player, spell.getId(), target, s);
        } else {
            WandBeamSpellHandlers.handleCrucioChannel(player, spell, target, s, channelEffectInterval);
        }
    }

    public static void endChannel(ServerPlayer player) {
        WandBeamSession s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        runChannelEndEffects(player, s);
        WandBeamSpellHandlers.clearSessionEffects(player, s);
    }

    /**
     * F2: runs a channel spell's authored effect entries on the beam cadence — {@code start} once on
     * the first channel tick, {@code tick} on the existing channel-effect interval (entity-targeted:
     * tick entries fire only while the beam holds a living target, matching the legacy per-tick
     * handlers). Scaling multipliers are re-resolved from the live proficiency profile each
     * invocation. No-op for spells without an {@code effects} list, so legacy/Java channel behavior
     * is untouched. BEAM_LETHAL never reaches this.
     */
    private static void runChannelEffects(ServerLevel level, ServerPlayer player, Spell spell,
                                          WandBeamSession s, @Nullable LivingEntity target,
                                          int channelEffectInterval) {
        List<SpellEffectEntry> effects = SpellEffectRunner.effectsOf(spell);
        if (effects.isEmpty()) return;
        if (s.beamTicks == 1) {
            SpellEffectRunner.run(effects, channelContext(level, player, spell, target), EffectCadence.START);
        }
        if (target != null && s.beamTicks % channelEffectInterval == 0) {
            SpellEffectRunner.run(effects, channelContext(level, player, spell, target), EffectCadence.TICK);
        }
    }

    /**
     * F2: fires a channel spell's {@code end} entries once, on release/interruption or when the
     * held spell switches mid-channel. Uses the session's cached target when it still resolves to a
     * living entity, else the caster.
     */
    private static void runChannelEndEffects(ServerPlayer player, WandBeamSession s) {
        if (s.spellId == null || s.beamTicks <= 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        Spell spell = Spells.byId(s.spellId);
        if (spell == null) return;
        SpellProperties props = spell.getProperties();
        if (props == null || props.getCastType() != CastType.BEAM_CHANNEL) return;
        List<SpellEffectEntry> effects = SpellEffectRunner.effectsOf(spell);
        if (effects.isEmpty()) return;
        LivingEntity target = s.cachedTarget == null ? null
                : WandBeamSpellHandlers.findLivingInLevel(player, s.cachedTarget);
        SpellEffectRunner.run(effects, channelContext(level, player, spell, target), EffectCadence.END);
    }

    /**
     * Per-tick application envelope: subject = current beam target (else caster), scaling
     * multipliers re-read from the live {@link ProficiencyScaler} profile so per-tick damage and
     * durations track proficiency exactly like cast-time invocations do.
     */
    private static SpellEffectContext channelContext(ServerLevel level, ServerPlayer player, Spell spell,
                                                     @Nullable LivingEntity target) {
        SpellScalingProfile profile = ProficiencyScaler.getProfileForPlayer(player, spellKey(spell.getId()));
        SpellEffectContext ctx = target != null
                ? SpellEffectContext.ofTarget(player, target, level)
                : SpellEffectContext.ofSelf(player, level);
        return ctx.withScaling(profile.damageMult(), profile.durationMult(), profile.controlMult());
    }

    /** Mirrors {@code SpellCastService}'s spell-key resolution for the proficiency profile. */
    private static Identifier spellKey(String spellId) {
        try {
            return Identifier.parse(spellId);
        } catch (Exception ignored) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, spellId);
        }
    }

    public static void adjustLeviosaDistance(ServerPlayer player, float delta) {
        if (Math.abs(delta) < 1.0e-4f) return;
        WandBeamSession s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        String spellId = s.spellId;
        if (spellId == null) {
            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
            spellId = data.getActiveSpellId();
        }
        Spell spell = spellId == null ? null : Spells.byId(spellId);
        if (spell == null || !WandBeamSpellIds.isLeviosa(spell.getId())) {
            return;
        }

        ItemStack wand = player.getUseItem();
        if (!(wand.getItem() instanceof WandItem)) {
            if (player.getMainHandItem().getItem() instanceof WandItem) {
                wand = player.getMainHandItem();
            } else if (player.getOffhandItem().getItem() instanceof WandItem) {
                wand = player.getOffhandItem();
            } else {
                return;
            }
        }
        float range = spell.getProperties().getRange() * WandStatsResolver.resolve(wand).rangeFor(spell);
        if (range <= 0f) range = 32f;
        float maxReach = Math.min(range, s.beamTicks * BeamRayResolver.extensionBlocksPerTick());
        float cap = Math.max(WandBeamSpellHandlers.LEVIOSA_MIN_DISTANCE,
                Math.min(maxReach, WandBeamSpellHandlers.LEVIOSA_MAX_DISTANCE));
        s.leviosaHoldDistance = Mth.clamp(s.leviosaHoldDistance + delta,
                WandBeamSpellHandlers.LEVIOSA_MIN_DISTANCE, cap);
    }
}
