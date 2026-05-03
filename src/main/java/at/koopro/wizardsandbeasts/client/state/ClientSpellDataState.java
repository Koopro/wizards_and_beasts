package at.koopro.wizardsandbeasts.client.state;

import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.SpellDataDeltaS2CPacket;
import at.koopro.wizardsandbeasts.network.SpellDataSyncS2CPacket;

import java.util.Map;

public final class ClientSpellDataState {
    private static final PlayerSpellData INSTANCE = new PlayerSpellData();
    private static int lastSyncVersion;

    private ClientSpellDataState() {}

    public static PlayerSpellData get() {
        return INSTANCE;
    }

    public static void applyFullSync(SpellDataSyncS2CPacket pkt) {
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
        for (Map.Entry<String, Long> e : pkt.cooldowns().entrySet()) {
            INSTANCE.setCooldown(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.castCounts().entrySet()) {
            INSTANCE.setCastCount(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.successfulHits().entrySet()) {
            INSTANCE.setSuccessfulHits(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : pkt.rejectCounts().entrySet()) {
            INSTANCE.setRejectCount(e.getKey(), e.getValue());
        }
        INSTANCE.setSyncCorrections(pkt.syncCorrections());
    }

    public static void applyDelta(SpellDataDeltaS2CPacket pkt) {
        INSTANCE.setCooldown(pkt.spellId(), pkt.cooldownExpiryTick());
        INSTANCE.setCastCount(pkt.spellId(), pkt.newCastCount());
        INSTANCE.setSuccessfulHits(pkt.spellId(), pkt.newSuccessfulHits());
    }
}
