package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> FIRE_EMBER =
            registerTint("fire_ember");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> ICE_SHARD =
            registerTint("ice_shard");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> ELECTRIC_ARC =
            registerTint("electric_arc");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> ARCANE_MOTE =
            registerTint("arcane_mote");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> DARK_WISP =
            registerTint("dark_wisp");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> LIGHT_GLOW =
            registerTint("light_glow");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> WATER_DROPLET =
            registerTint("water_droplet");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> SPELL_CLASH =
            registerTint("spell_clash");
    public static final DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> PROTEGO_DEFLECT =
            registerTint("protego_deflect");

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PROTEGO_SHATTER =
            PARTICLE_TYPES.register("protego_shatter", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AK_BYPASS_FLASH =
            PARTICLE_TYPES.register("ak_bypass_flash", () -> new SimpleParticleType(true));

    private static DeferredHolder<ParticleType<?>, ParticleType<SpellTintParticleOptions>> registerTint(String name) {
        return PARTICLE_TYPES.register(name, () -> new ParticleType<>(false) {
            @Override
            public MapCodec<SpellTintParticleOptions> codec() {
                return SpellTintParticleOptions.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, SpellTintParticleOptions> streamCodec() {
                return SpellTintParticleOptions.streamCodec(this);
            }
        });
    }

    public static ParticleType<SpellTintParticleOptions> typeFor(SpellFamily family) {
        return switch (family) {
            case FIRE -> FIRE_EMBER.get();
            case ICE -> ICE_SHARD.get();
            case ELECTRIC -> ELECTRIC_ARC.get();
            case ARCANE -> ARCANE_MOTE.get();
            case DARK -> DARK_WISP.get();
            case LIGHT -> LIGHT_GLOW.get();
            case WATER -> WATER_DROPLET.get();
        };
    }

    public static SpellTintParticleOptions tinted(SpellFamily family, int argb) {
        return new SpellTintParticleOptions(typeFor(family), argb);
    }

    private ModParticles() {}
}
