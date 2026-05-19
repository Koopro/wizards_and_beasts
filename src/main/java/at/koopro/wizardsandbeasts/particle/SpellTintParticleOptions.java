package at.koopro.wizardsandbeasts.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record SpellTintParticleOptions(ParticleType<SpellTintParticleOptions> type, int argb) implements ParticleOptions {

    public static MapCodec<SpellTintParticleOptions> codec(ParticleType<SpellTintParticleOptions> particleType) {
        return RecordCodecBuilder.mapCodec(inst -> inst.group(
                        Codec.INT.fieldOf("color").forGetter(SpellTintParticleOptions::argb))
                .apply(inst, rgb -> new SpellTintParticleOptions(particleType, rgb)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, SpellTintParticleOptions> streamCodec(
            ParticleType<SpellTintParticleOptions> particleType) {
        return StreamCodec.of(
                (buf, o) -> buf.writeVarInt(o.argb()),
                buf -> new SpellTintParticleOptions(particleType, buf.readVarInt()));
    }

    @Override
    public ParticleType<SpellTintParticleOptions> getType() {
        return type;
    }
}
