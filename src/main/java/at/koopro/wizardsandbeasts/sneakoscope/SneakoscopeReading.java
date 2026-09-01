package at.koopro.wizardsandbeasts.sneakoscope;

/**
 * The outcome of one sweep: how many suspicious things were in range, and which sector the nearest
 * of them lay in.
 *
 * @param threats number of suspicious entities found
 * @param bearing sector of the nearest one, or {@link SneakoscopeTuning#NO_BEARING}
 */
public record SneakoscopeReading(int threats, int bearing) {

    public static final SneakoscopeReading QUIET = new SneakoscopeReading(0, SneakoscopeTuning.NO_BEARING);

    public SneakoscopeTier tier() {
        return SneakoscopeTuning.tier(threats);
    }
}
