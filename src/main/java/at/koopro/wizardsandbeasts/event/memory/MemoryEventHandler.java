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

    // ---- A12 painful memories (owner, 2026-11-25, option 1): formed by real harm, one per kind per day ----
    private static final long  PAIN_COOLDOWN       = 24000L;  // one in-game day per kind
    private static final float DEMENTOR_INTENSITY  = 0.5f;
    private static final float CRUCIO_INTENSITY    = 0.7f;
    private static final float NEAR_DEATH_INTENSITY = 0.4f;
    public static final float COMPANION_INTENSITY  = 0.8f;
    /** Left at two hearts or less by a blow of at least two hearts. */
    private static final float NEAR_DEATH_HEALTH = 4.0f;
    private static final float NEAR_DEATH_BLOW   = 4.0f;

    /** Forms a painful memory and says so, quietly. */
    public static void pain(ServerPlayer player, float intensity, String key, long cooldown) {
        if (MemoryService.tryFormMemory(player, MemoryType.PAINFUL, intensity, key, cooldown)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "memory.wizards_and_beasts.painful.formed").withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
        // A Swooping Evil's sting draws the newest bad memory out (Fantastic Beasts: its venom erases bad memories).
        if (attacker != null && "swooping_evil".equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).getPath())) {
            if (MemoryService.forgetNewestPainful(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "memory.wizards_and_beasts.painful.forgotten").withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
            return;
        }
        if (attacker instanceof at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity) {
            pain(player, DEMENTOR_INTENSITY, "dementor", PAIN_COOLDOWN);
        }
        if (player.isAlive() && player.getHealth() <= NEAR_DEATH_HEALTH && event.getNewDamage() >= NEAR_DEATH_BLOW) {
            pain(player, NEAR_DEATH_INTENSITY, "near_death", PAIN_COOLDOWN);
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffectInstance().is(at.koopro.wizardsandbeasts.effect.ModEffects.CRUCIO_SANITY_DRAIN)) {
            pain(player, CRUCIO_INTENSITY, "cruciatus", PAIN_COOLDOWN);
        }
    }
}
