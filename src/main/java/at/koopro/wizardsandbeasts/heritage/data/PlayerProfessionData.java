package at.koopro.wizardsandbeasts.heritage.data;

import at.koopro.wizardsandbeasts.owl.Profession;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The career chosen after the O.W.L.s, and its pay record.
 *
 * @param profession      the career, or {@code null} before one is chosen
 * @param chosenAtSitting the exam sitting ({@code PlayerOWLData.sittings}) the career was chosen after;
 *                        a later sitting reopens the choice. Saves from before resits load as 0, so a
 *                        career chosen under the old rules may be chosen again once.
 * @param lastPaidDay     the in-game day ({@code gameTime / 24000}) the last wage was paid; -1 for never
 */
public record PlayerProfessionData(
        @Nullable Profession profession,
        int chosenAtSitting,
        long lastPaidDay
) {
    private static final Codec<Profession> PROFESSION_CODEC = Codec.STRING.xmap(
            s -> Profession.valueOf(s.toUpperCase()),
            Profession::name);

    public static final Codec<PlayerProfessionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PROFESSION_CODEC.optionalFieldOf("profession")
                    .forGetter(d -> Optional.ofNullable(d.profession())),
            Codec.INT.optionalFieldOf("chosenAtSitting", 0)
                    .forGetter(PlayerProfessionData::chosenAtSitting),
            Codec.LONG.optionalFieldOf("lastPaidDay", -1L)
                    .forGetter(PlayerProfessionData::lastPaidDay)
    ).apply(instance, (opt, sitting, paid) -> new PlayerProfessionData(opt.orElse(null), sitting, paid)));

    public static final PlayerProfessionData DEFAULT = new PlayerProfessionData(null, 0, -1L);

    /** A career chosen after {@code sitting}, not yet paid. */
    public static PlayerProfessionData chosen(Profession profession, int sitting) {
        return new PlayerProfessionData(profession, sitting, -1L);
    }

    public PlayerProfessionData paidOn(long day) {
        return new PlayerProfessionData(profession, chosenAtSitting, day);
    }
}
