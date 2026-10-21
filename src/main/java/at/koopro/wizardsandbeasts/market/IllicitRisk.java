package at.koopro.wizardsandbeasts.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * How likely an under-the-counter deal is to reach the Ministry, with no game attached.
 *
 * <p>A location that declares a {@code "risk"} block deals outside the law: every deal there rolls once against
 * {@link #chance}. The roll, the offence it files and what follows (heat, a fine, the permanent file, standing) all
 * belong to the existing Ministry pipeline ({@code TraceService.report}); this class only says how likely.
 *
 * <pre>
 *   p = base · class weight · volume · eyes · heat · (1 − discretion) · scale        (clamped to 0 … 0.95)
 *   volume = 1 + 0.35 · ln(units)      a bigger deal is likelier to be talked about, but sub-linearly:
 *                                      splitting a sale into many small ones is riskier, not safer
 *   eyes   = (1 + 2 · officials) · (1 + 0.25 · witnesses)     officials and other wizards in sight, capped
 *   heat   = 1 + notoriety / 50        a wizard the Ministry already watches is watched more closely
 * </pre>
 *
 * @param chance       base chance of one plain deal being reported
 * @param watchRadius  blocks within which officials and witnesses count
 */
@NullMarked
public record IllicitRisk(float chance, double watchRadius) {

    public static final Codec<IllicitRisk> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0f, 1.0f).fieldOf("chance").forGetter(IllicitRisk::chance),
            Codec.doubleRange(1.0, 64.0).optionalFieldOf("watch_radius", 16.0).forGetter(IllicitRisk::watchRadius)
    ).apply(i, IllicitRisk::new));

    /** The most any single deal can be: there is always a chance nobody talks. */
    public static final double CEILING = 0.95;
    /** Officials beyond this many add nothing: one Auror seeing it is enough. */
    public static final int MAX_OFFICIALS = 3;
    /** Witnesses beyond this many add nothing. */
    public static final int MAX_WITNESSES = 8;
    /** Discretion can blunt attention, never blind it (the Trace's own cap). */
    public static final double MAX_DISCRETION = 0.6;

    /**
     * @param units      units in the deal (1 for a service)
     * @param officials  Ministry officials in sight
     * @param witnesses  other wizards and Muggles in sight
     * @param notoriety  the dealer's heat, 0–100
     * @param discretion the dealer's {@code TRACE_DISCRETION}
     * @param scale      the server's detection scale (1.0 = as written)
     */
    public double chance(DealClass deal, int units, int officials, int witnesses, float notoriety, double discretion,
                         double scale) {
        if (deal == DealClass.LEGAL || units <= 0 || scale <= 0.0) {
            return 0.0;
        }
        double volume = 1.0 + 0.35 * Math.log(units);
        double eyes = (1.0 + 2.0 * Math.clamp(officials, 0, MAX_OFFICIALS))
                * (1.0 + 0.25 * Math.clamp(witnesses, 0, MAX_WITNESSES));
        double heat = 1.0 + Math.clamp(notoriety, 0.0f, 100.0f) / 50.0;
        double hidden = 1.0 - Math.clamp(discretion, 0.0, MAX_DISCRETION);
        double p = chance * deal.weight() * volume * eyes * heat * hidden * scale;
        return Math.clamp(p, 0.0, CEILING);
    }
}
