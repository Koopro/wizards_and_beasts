package at.koopro.wizardsandbeasts.client.stats;

import at.koopro.wizardsandbeasts.client.gui.toast.WizardsToasts;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a player sees and hears when one of their stats goes up.
 *
 * <p>Earning a point used to be invisible: the number in the attachment changed, the sync packet
 * carried it, and unless the character sheet happened to be open at that instant nothing said so.
 * Four channels now fire off one packet, none of them individually loud:
 *
 * <ul>
 *   <li><b>A toast.</b> The only channel that survives an open screen, which matters because the
 *       sheet is exactly where a player might be standing when a point lands.</li>
 *   <li><b>A sound.</b> One quiet amethyst chime, pitched by how high the stat now is, played on
 *       the UI channel so it neither attenuates with distance nor gets buried under combat.</li>
 *   <li><b>Particles.</b> A dozen enchant glyphs settling onto the player. Client-only; no entity
 *       event, no second packet.</li>
 *   <li><b>A row flash.</b> The character sheet reads {@link #flashStrength} and lifts that stat's
 *       row for a second and a bit, so opening the sheet right after a level-up shows you which
 *       number moved.</li>
 * </ul>
 *
 * <p>Nothing here fires for ordinary training progress. That is the whole reason a point is worth
 * announcing: a spell hit happens hundreds of times and must stay silent, and the 1-in-N hit that
 * finishes a point is the one that has earned a noise.
 */
@NullMarked
public final class ClientStatLevelUps {

    /** How long a stat row stays lit after its point lands. */
    private static final long FLASH_MS = 1_200L;
    private static final int PARTICLE_COUNT = 12;

    private static final Map<PlayerStat, Long> lastLevelUp = new EnumMap<>(PlayerStat.class);

    private ClientStatLevelUps() {}

    /** Called from the payload handler on the client thread. */
    public static void onLevelUp(PlayerStat stat, int from, int to, String sourceKey) {
        lastLevelUp.put(stat, System.currentTimeMillis());

        Component title = Component.translatable("message.wizards_and_beasts.stat_up.title",
                stat.displayName().copy().withStyle(ChatFormatting.GOLD), to - from);
        Component body = sourceKey.isEmpty()
                ? Component.translatable("message.wizards_and_beasts.stat_up.body", from, to)
                : Component.translatable("message.wizards_and_beasts.stat_up.body_sourced",
                        from, to, Component.translatable(sourceKey));
        WizardsToasts.show(NoticeKind.UNLOCK, title, body);

        Minecraft mc = Minecraft.getInstance();
        // Pitch rises with the stat, so the hundredth point sounds different from the first. Kept
        // inside a fifth: any wider and the low end reads as a mistake rather than as a low note.
        float pitch = 1.0f + 0.5f * Mth.clamp(to / 100.0f, 0f, 1f);
        mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.45f));

        spawnParticles(mc);
    }

    private static void spawnParticles(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        var random = player.getRandom();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            // Enchantment glyphs drift *towards* their target, so they are seeded on a shell around
            // the player and converge on them — the same read as an enchanting table.
            double ox = (random.nextDouble() - 0.5) * 2.0;
            double oy = random.nextDouble() * 2.0;
            double oz = (random.nextDouble() - 0.5) * 2.0;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                    player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                    -ox * 0.5, -oy * 0.25, -oz * 0.5);
        }
    }

    /**
     * How brightly this stat's row should be lit right now: 1 immediately after a point lands,
     * falling linearly to 0 over {@link #FLASH_MS}. Cheap enough to call once per row per frame.
     */
    public static float flashStrength(PlayerStat stat) {
        Long at = lastLevelUp.get(stat);
        if (at == null) return 0f;
        long elapsed = System.currentTimeMillis() - at;
        if (elapsed < 0 || elapsed >= FLASH_MS) return 0f;
        return 1.0f - (float) elapsed / FLASH_MS;
    }

    /** Dropped on disconnect along with the rest of the client stat state. */
    public static void clear() {
        lastLevelUp.clear();
    }
}
