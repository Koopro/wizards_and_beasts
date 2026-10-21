package at.koopro.wizardsandbeasts.location;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * One service a location offers, as its data file configures it. The behaviour is Java
 * ({@link LocationServices}); the file decides who may use it and what it costs there.
 *
 * <pre>{@code
 * { "service": "wizards_and_beasts:curse_breaking", "min_tier": "customer",
 *   "rate": 0.35, "rate_per_tier": -0.05 }
 * }</pre>
 *
 * @param service         the registered behaviour
 * @param minTier         the lowest standing tier allowed to use it
 * @param feeKnuts        a flat fee, before the tier discount; what the fee buys is the service's own business
 * @param discountPerTier fraction knocked off the flat fee for every tier held above {@code minTier}
 * @param rate            a fraction the service applies to a value (a share of an artefact's worth, say)
 * @param ratePerTier     added to {@code rate} for every tier above {@code minTier}; negative to fall
 */
@NullMarked
public record ServiceOffer(Identifier service, String minTier, long feeKnuts, float discountPerTier, float rate,
                           float ratePerTier) {

    public static final Codec<ServiceOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("service").forGetter(ServiceOffer::service),
            Codec.STRING.fieldOf("min_tier").forGetter(ServiceOffer::minTier),
            Codec.intRange(0, 493 * 1000).optionalFieldOf("fee_knuts", 0).forGetter(o -> (int) o.feeKnuts()),
            Codec.floatRange(0.0f, 0.25f).optionalFieldOf("discount_per_tier", 0.0f)
                    .forGetter(ServiceOffer::discountPerTier),
            Codec.floatRange(0.0f, 1.0f).optionalFieldOf("rate", 0.0f).forGetter(ServiceOffer::rate),
            Codec.floatRange(-0.5f, 0.5f).optionalFieldOf("rate_per_tier", 0.0f).forGetter(ServiceOffer::ratePerTier)
    ).apply(i, (service, minTier, fee, discount, rate, perTier) ->
            new ServiceOffer(service, minTier, fee, discount, rate, perTier)));

    /** The flat fee for someone {@code tiersAbove} tiers above the minimum; never below half the fee. */
    public long feeFor(int tiersAbove) {
        double discount = Math.min(0.5, discountPerTier * (double) Math.max(0, tiersAbove));
        return Math.round(feeKnuts * (1.0 - discount));
    }

    /** The rate for someone {@code tiersAbove} tiers above the minimum, kept within 0–1. */
    public float rateFor(int tiersAbove) {
        return Math.clamp(rate + ratePerTier * Math.max(0, tiersAbove), 0.0f, 1.0f);
    }
}
