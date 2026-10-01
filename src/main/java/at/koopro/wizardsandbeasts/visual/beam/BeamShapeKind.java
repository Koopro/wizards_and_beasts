package at.koopro.wizardsandbeasts.visual.beam;

/**
 * The beam forms the one beam renderer ({@code client.beam}) can draw: a straight {@code Laser} or a jagged
 * {@code Lightning} bolt. A new form is a new shape record in {@code client.beam} plus a constant here.
 */
public enum BeamShapeKind {
    LASER,
    LIGHTNING
}
