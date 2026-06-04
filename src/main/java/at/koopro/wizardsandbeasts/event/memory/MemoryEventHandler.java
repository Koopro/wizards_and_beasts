package at.koopro.wizardsandbeasts.event.memory;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryTierAdvancedEvent;   // korrigiert (Schritt 0a): event.bestiary, nicht bestiary.event
import at.koopro.wizardsandbeasts.memory.MemoryService;
import at.koopro.wizardsandbeasts.memory.MemoryType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;          // bestätigt (Schritt 0b)
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MemoryEventHandler {

    // Tunable: Cooldowns in Ticks (20/sec), Intensitäten 0..1.
    private static final long  BESTIARY_COOLDOWN  = 0L;     // per-Entry-Key → natürlich one-shot pro Kreatur
    private static final float BESTIARY_INTENSITY = 0.6f;
    private static final long  LEVELUP_COOLDOWN   = 2400L;  // 2 min — bremst XP-Farm-Spam
    private static final float LEVELUP_INTENSITY  = 0.4f;
    private static final long  SLEEP_COOLDOWN     = 12000L; // 10 min — eine erholte Erinnerung pro Schlafzyklus
    private static final float SLEEP_INTENSITY    = 0.3f;

    private MemoryEventHandler() {}

    @SubscribeEvent
    public static void onBestiaryTierAdvanced(BestiaryTierAdvancedEvent event) {
        if (!(event.player() instanceof ServerPlayer player)) return;        // korrigiert (Schritt 0a): player()
        String key = "bestiary:" + event.entryId();                          // korrigiert (Schritt 0a): entryId()
        MemoryService.tryFormMemory(player, MemoryType.HAPPY, BESTIARY_INTENSITY, key, BESTIARY_COOLDOWN);
    }

    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getLevels() <= 0) return;
        MemoryService.tryFormMemory(player, MemoryType.HAPPY, LEVELUP_INTENSITY, "levelup", LEVELUP_COOLDOWN);
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MemoryService.tryFormMemory(player, MemoryType.HAPPY, SLEEP_INTENSITY, "sleep", SLEEP_COOLDOWN);
    }
}
