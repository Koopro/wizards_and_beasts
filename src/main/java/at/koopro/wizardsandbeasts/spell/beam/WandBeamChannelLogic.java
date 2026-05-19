package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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
                Entity scanned = WandBeamSpellHandlers.findLeviosaTargetAlongCrosshair(player, maxReach);
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
        WandBeamSpellHandlers.clearSessionEffects(player, s);
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
