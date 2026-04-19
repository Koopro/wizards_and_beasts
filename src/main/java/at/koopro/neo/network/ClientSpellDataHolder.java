package at.koopro.neo.network;

import at.koopro.neo.data.PlayerSpellData;

import java.util.Map;

/**
 * Client-side cache of the local player's spell data, kept in sync via
 * {@link SpellDataSyncS2CPacket} (full snapshot) and
 * {@link SpellDataDeltaS2CPacket} (per-cast delta).
 */
public final class ClientSpellDataHolder {

    private static final PlayerSpellData INSTANCE = new PlayerSpellData();

    private ClientSpellDataHolder() {}

    public static PlayerSpellData get() {
        return INSTANCE;
    }

    /** Replaces all client-side state with the contents of a server snapshot. */
    public static void applyFullSync(SpellDataSyncS2CPacket pkt) {
        INSTANCE.resetAll();
        for (String id : pkt.knownSpells()) {
            INSTANCE.learnSpell(id);
        }
        for (int i = 0; i < pkt.loadout().length && i < PlayerSpellData.LOADOUT_SIZE; i++) {
            INSTANCE.setLoadoutSpell(i, pkt.loadout()[i]);
        }
        INSTANCE.setActiveSlot(pkt.activeSlot());
        for (Map.Entry<String, Long> e : pkt.cooldowns().entrySet()) {
            INSTANCE.setCooldown(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.castCounts().entrySet()) {
            INSTANCE.setCastCount(e.getKey(), e.getValue());
        }
    }

    /** Applies a per-cast delta: refresh cooldown + cast count for one spell. */
    public static void applyDelta(SpellDataDeltaS2CPacket pkt) {
        INSTANCE.setCooldown(pkt.spellId(), pkt.cooldownExpiryTick());
        INSTANCE.setCastCount(pkt.spellId(), pkt.newCastCount());
    }
}
