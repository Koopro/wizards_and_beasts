package at.koopro.wizardsandbeasts.sneakoscope;

/**
 * How hard the Sneakoscope is spinning, derived from the threat count of the last scan.
 *
 * <p>The bands are the ones the item's whole presentation is built on — sound, particles and the
 * holder's screen all key off this enum rather than off the raw count, so "what does three
 * suspicious people look like" has exactly one answer and it is spelled out here.
 */
public enum SneakoscopeTier {
    /** Nothing suspicious. The top rests. */
    CALM,
    /** One or two. A slow turn and a soft whirr — enough to make you look around. */
    LOW,
    /** Three to five. Faster, louder, silver light starts to orbit the glass. */
    MEDIUM,
    /** Six or more. A continuous shriek and a red pulse only the holder sees. */
    HIGH
}
