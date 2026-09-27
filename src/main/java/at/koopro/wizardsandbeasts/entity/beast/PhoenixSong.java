package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Phoenix song.
 *
 * <p>Canon: the song "is magical; it is reputed to increase the courage of the pure of heart and to strike fear
 * into the hearts of the impure" (Fantastic Beasts); in the Chamber of Secrets it gives Harry his nerve. A phoenix
 * here sings when its person is in danger. Kept small on purpose: those it sings for take less harm for ten seconds
 * and the despair of a Dementor's chill or magical darkness lifts from them; monsters within earshot are weakened
 * for as long. Nothing else — it is a song, not a spell.
 */
public final class PhoenixSong {

    /** One phrase: note offsets on the scale, one per {@link #NOTE_INTERVAL} ticks. */
    private static final float[] PHRASE = {1.0f, 1.19f, 1.33f, 1.5f, 1.33f, 1.78f, 1.5f, 2.0f};
    static final int NOTE_INTERVAL = 7;
    private static final int EFFECT_TICKS = 200;

    private PhoenixSong() {}

    /** The first bar: effects on everyone in earshot, once per song. */
    public static void begin(ServerLevel level, PhoenixEntity phoenix) {
        AABB area = phoenix.getBoundingBox().inflate(WildlifeRules.SONG_RANGE);
        LivingEntity attacker = phoenix.getLastHurtByMob();
        for (Player player : level.getEntitiesOfClass(Player.class, area, EntitySelector.NO_SPECTATORS)) {
            if (player == attacker) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, EFFECT_TICKS, 0), phoenix);
            player.removeEffect(ModEffects.DEMENTOR_CHILL);
            player.removeEffect(MobEffects.DARKNESS);
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m instanceof Enemy && m.isAlive())) {
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 0), phoenix);
        }
    }

    /** One note of the phrase, if this tick of the song falls on one. */
    public static void note(ServerLevel level, PhoenixEntity phoenix, int songTick) {
        if (songTick % NOTE_INTERVAL != 0) {
            return;
        }
        int index = (songTick / NOTE_INTERVAL) % PHRASE.length;
        float pitch = PHRASE[index] * 0.75f;
        level.playSound(null, phoenix.getX(), phoenix.getEyeY(), phoenix.getZ(), ModSounds.PHOENIX_SONG.get(),
                SoundSource.NEUTRAL, 1.2f, pitch);
        level.sendParticles(ParticleTypes.NOTE, phoenix.getX(), phoenix.getEyeY() + 0.6, phoenix.getZ(), 1,
                0.0, 0.0, 0.0, index / 24.0);
    }
}
