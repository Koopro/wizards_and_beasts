package at.koopro.wizardsandbeasts.client.atmosphere;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The particles an atmosphere profile may name: the mod's own tinted sprites. Vanilla's sparkles keep their vanilla
 * meaning (style guide §3), so a profile cannot ask for them; an unknown id is skipped with one warning at load.
 */
@NullMarked
public final class AtmosphereParticles {

    private static final Map<String, Supplier<ParticleType<SpellTintParticleOptions>>> TYPES = Map.ofEntries(
            Map.entry("firefly", () -> ModParticles.FIREFLY.get()),
            Map.entry("wisp", () -> ModParticles.WISP.get()),
            Map.entry("mote", () -> ModParticles.MOTE.get()),
            Map.entry("glint", () -> ModParticles.GLINT.get()),
            Map.entry("dust", () -> ModParticles.DUST.get()),
            Map.entry("mist", () -> ModParticles.MIST.get()),
            Map.entry("arcane_mote", () -> ModParticles.ARCANE_MOTE.get()),
            Map.entry("dark_wisp", () -> ModParticles.DARK_WISP.get()),
            Map.entry("light_glow", () -> ModParticles.LIGHT_GLOW.get()),
            Map.entry("smoke_puff", () -> ModParticles.SMOKE_PUFF.get()),
            Map.entry("fire_ember", () -> ModParticles.FIRE_EMBER.get()),
            Map.entry("water_droplet", () -> ModParticles.WATER_DROPLET.get()));

    private AtmosphereParticles() {}

    /** Ids a profile may use, without the namespace. */
    public static Set<String> names() {
        return TYPES.keySet();
    }

    public static boolean known(Identifier id) {
        return id.getNamespace().equals(WizardsAndBeastsMod.MODID) && TYPES.containsKey(id.getPath());
    }

    public static @Nullable SpellTintParticleOptions options(Identifier id, int argb) {
        if (!known(id)) {
            return null;
        }
        return new SpellTintParticleOptions(TYPES.get(id.getPath()).get(), argb);
    }
}
