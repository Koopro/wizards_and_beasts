package at.koopro.wizardsandbeasts.wand.cast;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

public record WandAllegiance(@Nullable UUID boundPlayer, float bondStrength, long firstBondTick) {
    public static final Codec<WandAllegiance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("boundPlayer")
                    .xmap(o -> o.map(WandAllegiance::parseUuid).orElse(null),
                            u -> Optional.ofNullable(u).map(UUID::toString))
                    .forGetter(WandAllegiance::boundPlayer),
            Codec.FLOAT.optionalFieldOf("bondStrength", 0.0f).forGetter(WandAllegiance::bondStrength),
            Codec.LONG.optionalFieldOf("firstBondTick", 0L).forGetter(WandAllegiance::firstBondTick)
    ).apply(inst, WandAllegiance::new));

    public static final StreamCodec<ByteBuf, WandAllegiance> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WandAllegiance decode(ByteBuf buf) {
            String bound = net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf);
            float bond = net.minecraft.network.codec.ByteBufCodecs.FLOAT.decode(buf);
            long firstTick = net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.decode(buf);
            return new WandAllegiance(parseUuid(bound), bond, firstTick);
        }

        @Override
        public void encode(ByteBuf buf, WandAllegiance value) {
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(
                    buf,
                    value.boundPlayer() == null ? "" : value.boundPlayer().toString());
            net.minecraft.network.codec.ByteBufCodecs.FLOAT.encode(buf, value.bondStrength());
            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.encode(buf, value.firstBondTick());
        }
    };

    public WandAllegiance {
        if (bondStrength < 0.0f) {
            bondStrength = 0.0f;
        } else if (bondStrength > 1.0f) {
            bondStrength = 1.0f;
        }
    }

    public static WandAllegiance unbound() {
        return new WandAllegiance(null, 0.0f, 0L);
    }

    public boolean isBound() {
        return boundPlayer != null;
    }

    public WandAllegiance withBoundPlayer(@Nullable UUID player, long gameTick) {
        return new WandAllegiance(player, bondStrength, player == null ? 0L : gameTick);
    }

    public WandAllegiance withBondStrength(float value) {
        return new WandAllegiance(boundPlayer, value, firstBondTick);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
