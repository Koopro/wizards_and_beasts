package at.koopro.wizardsandbeasts.heritage.data;

import at.koopro.wizardsandbeasts.owl.Profession;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record PlayerProfessionData(
        @Nullable Profession profession
) {
    private static final Codec<Profession> PROFESSION_CODEC = Codec.STRING.xmap(
            s -> Profession.valueOf(s.toUpperCase()),
            Profession::name);

    public static final Codec<PlayerProfessionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PROFESSION_CODEC.optionalFieldOf("profession")
                    .forGetter(d -> Optional.ofNullable(d.profession()))
    ).apply(instance, opt -> new PlayerProfessionData(opt.orElse(null))));

    public static final PlayerProfessionData DEFAULT = new PlayerProfessionData(null);
}
