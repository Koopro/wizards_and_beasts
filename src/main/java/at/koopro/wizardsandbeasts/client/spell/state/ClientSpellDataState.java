package at.koopro.wizardsandbeasts.client.spell.state;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellProficiencySyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

public final class ClientSpellDataState {
    private static final PlayerSpellData INSTANCE = new PlayerSpellData();
    private static int lastSyncVersion;

    /**
     * Display-only: total duration (ticks) of each spell's <em>current</em> cooldown, captured at the
     * moment the cooldown (re)starts. The server only sends the absolute expiry tick, not the applied
     * duration — and the applied duration can differ from {@code baseCooldownTicks} by up to the
     * modifier cap (×3) — so the HUD sweep needs this to render the correct fraction instead of
     * clamping at "full" for long cooldowns. Keyed by spell id.
     */
    private static final Map<String, Long> cooldownSpanTicks = new HashMap<>();

    /**
     * Last expiry tick we applied per spell. Kept separate from {@link #INSTANCE} so that
     * {@code resetAll()} during a full sync does NOT make a re-pushed (identical) cooldown look like a
     * fresh start — which would otherwise snap the HUD sweep back to full on every full sync.
     */
    private static final Map<String, Long> lastExpiryTicks = new HashMap<>();

    private ClientSpellDataState() {}

    public static PlayerSpellData get() {
        return INSTANCE;
    }

    /**
     * Total ticks the active cooldown for {@code spellId} runs for, used as the HUD sweep denominator.
     * Falls back to {@code fallbackTicks} (the spell's base cooldown) when the span is unknown — e.g.
     * a cooldown that was already in progress before this client observed it start.
     */
    public static long getCooldownSpanTicks(String spellId, long fallbackTicks) {
        long span = cooldownSpanTicks.getOrDefault(spellId, 0L);
        return span > 0 ? span : Math.max(1L, fallbackTicks);
    }

    /**
     * Records the cooldown span only when the cooldown genuinely (re)starts, i.e. the expiry tick
     * moves later. Idempotent re-sends that carry the same expiry (e.g. proficiency-hit deltas) leave
     * the span untouched, so the sweep keeps counting down instead of snapping back to full.
     */
    private static void applyCooldown(String spellId, long newExpiry) {
        long oldExpiry = lastExpiryTicks.getOrDefault(spellId, 0L);
        if (newExpiry > oldExpiry) {
            // Genuine (re)start: expiry moved later than anything we've seen for this spell.
            cooldownSpanTicks.put(spellId, Math.max(1L, newExpiry - clientGameTime()));
        } else if (newExpiry <= 0L) {
            cooldownSpanTicks.remove(spellId);
        }
        lastExpiryTicks.put(spellId, newExpiry);
        INSTANCE.setCooldown(spellId, newExpiry);
    }

    private static long clientGameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    /** Largest client game tick observed, for monotonic cooldown rendering (see {@link #monotonicTick}). */
    private static long monotonicClientTick = Long.MIN_VALUE;

    /**
     * Game time is monotonic, but under server lag the client over-extrapolates {@code getGameTime()}
     * forward during a stall and then snaps it <em>backward</em> when the server catches up — which makes
     * a fixed-expiry cooldown sweep spring back toward full. Clamp to the largest tick seen so the HUD
     * countdown never reverses. A large backward jump (world change / rejoin) re-baselines.
     */
    public static long monotonicTick(long currentGameTick) {
        if (currentGameTick > monotonicClientTick || currentGameTick < monotonicClientTick - 1200L) {
            monotonicClientTick = currentGameTick;
        }
        return monotonicClientTick;
    }

    public static void applyFullSync(SpellDataSyncS2CPayload pkt) {
        if (pkt.syncVersion() < lastSyncVersion) {
            return;
        }
        lastSyncVersion = pkt.syncVersion();
        INSTANCE.resetAll();
        for (String id : pkt.knownSpells()) {
            INSTANCE.learnSpell(id);
        }
        for (int i = 0; i < pkt.loadout().length && i < PlayerSpellData.LOADOUT_SIZE; i++) {
            INSTANCE.setLoadoutSpell(i, pkt.loadout()[i]);
        }
        INSTANCE.setActiveSlot(pkt.activeSlot());
        // Zero out any tracked cooldown the full sync no longer reports, then apply the reported ones
        // through applyCooldown so the span/restart bookkeeping stays consistent (and a re-pushed,
        // unchanged cooldown does NOT re-trigger a "full" sweep).
        for (String tracked : new java.util.ArrayList<>(lastExpiryTicks.keySet())) {
            if (!pkt.cooldowns().containsKey(tracked)) {
                applyCooldown(tracked, 0L);
            }
        }
        for (Map.Entry<String, Long> e : pkt.cooldowns().entrySet()) {
            applyCooldown(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.castCounts().entrySet()) {
            INSTANCE.setCastCount(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.successfulHits().entrySet()) {
            INSTANCE.setSuccessfulHits(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Float> e : pkt.spellProficiencies().entrySet()) {
            INSTANCE.setSpellProficiency(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.rejectCounts().entrySet()) {
            INSTANCE.setRejectCount(e.getKey(), e.getValue());
        }
        INSTANCE.setSyncCorrections(pkt.syncCorrections());
    }

    public static void applyDelta(SpellDataDeltaS2CPayload pkt) {
        applyCooldown(pkt.spellId(), pkt.cooldownExpiryTick());
        INSTANCE.setCastCount(pkt.spellId(), pkt.newCastCount());
        INSTANCE.setSuccessfulHits(pkt.spellId(), pkt.newSuccessfulHits());
        INSTANCE.setGlobalCooldownEndTick(pkt.globalCooldownEndTick());
    }

    public static void applyProficiencyDelta(SpellProficiencySyncS2CPayload pkt) {
        INSTANCE.setSpellProficiency(pkt.spellId(), pkt.proficiency());
    }
}
