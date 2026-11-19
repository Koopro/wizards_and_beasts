package at.koopro.wizardsandbeasts.client.atmosphere;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;

/**
 * A place's room tone: a quiet loop, not positioned (it is the room, not a thing in it), whose volume follows a target
 * the director sets each tick and eases there over about two seconds. When the target has been zero and the volume
 * has run out, it stops itself; a place that comes back starts a new one.
 */
@NullMarked
public final class AmbientBed extends AbstractTickableSoundInstance {

    /** Volume change per tick: silence to the cap in about two seconds. */
    private static final float STEP = 0.004f;

    private float target;

    AmbientBed(SoundEvent sound, RandomSource random) {
        super(sound, SoundSource.AMBIENT, random);
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    void target(float volume) {
        this.target = volume;
    }

    /** Fade out and stop: the place is gone or the profiles were reloaded. */
    void release() {
        this.target = 0f;
    }

    @Override
    public void tick() {
        if (volume < target) {
            volume = Math.min(target, volume + STEP);
        } else if (volume > target) {
            volume = Math.max(target, volume - STEP);
        }
        if (target <= 0f && volume <= 0.0005f) {
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
