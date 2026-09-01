package at.koopro.wizardsandbeasts.client.particle;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class ModParticleProviders {

    private ModParticleProviders() {}

    public static void register(RegisterParticleProvidersEvent event) {
        register(event, ModParticles.FIRE_EMBER.get(), SpellFamily.FIRE);
        register(event, ModParticles.ICE_SHARD.get(), SpellFamily.ICE);
        register(event, ModParticles.ELECTRIC_ARC.get(), SpellFamily.ELECTRIC);
        register(event, ModParticles.ARCANE_MOTE.get(), SpellFamily.ARCANE);
        register(event, ModParticles.DARK_WISP.get(), SpellFamily.DARK);
        register(event, ModParticles.LIGHT_GLOW.get(), SpellFamily.LIGHT);
        register(event, ModParticles.WATER_DROPLET.get(), SpellFamily.WATER);
        register(event, ModParticles.PROTEGO_DEFLECT.get(), SpellFamily.ARCANE);

        // Broom slipstreams. One particle class, three palettes: what separates a Cleansweep's dust
        // from a Firebolt's embers is colour, size and how fast it fades, none of which needs its
        // own class. All three share the spell_mote sprite rather than shipping three PNGs that
        // would differ only in hue -- the tint does that at no cost.
        event.registerSpriteSet(ModParticles.BROOM_TRAIL_DUST.get(),
                sprites -> BroomTrailParticle.provider(sprites, BroomTrailParticle.Style.DUST));
        event.registerSpriteSet(ModParticles.BROOM_TRAIL_GOLD.get(),
                sprites -> BroomTrailParticle.provider(sprites, BroomTrailParticle.Style.GOLD));
        event.registerSpriteSet(ModParticles.BROOM_TRAIL_EMBER.get(),
                sprites -> BroomTrailParticle.provider(sprites, BroomTrailParticle.Style.EMBER));
    }

    private static void register(RegisterParticleProvidersEvent event,
                                 net.minecraft.core.particles.ParticleType<SpellTintParticleOptions> type,
                                 SpellFamily family) {
        event.registerSpriteSet(type, sprites -> new TintedSpriteProvider(sprites, family));
    }

    private record TintedSpriteProvider(SpriteSet sprites, SpellFamily family)
            implements ParticleProvider<SpellTintParticleOptions> {

        @Override
        public Particle createParticle(SpellTintParticleOptions options,
                                       net.minecraft.client.multiplayer.ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return FamilyTintParticle.create(level, x, y, z, options, family, sprites, random);
        }
    }
}
