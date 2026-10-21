package at.koopro.wizardsandbeasts.client.gui.ui;

/**
 * What a slot -- an item slot, a wand socket, a bound-spell row -- is showing, in the order a player
 * perceives it. One vocabulary so every slot on every screen answers the same questions the same way.
 *
 * <p>Each state has to be told apart without colour: a frame that changes weight or shape, a glyph,
 * an inset, a dimmed icon. Colour may reinforce a state; it never carries one alone.
 */
public enum UiSlotState {
    /** Nothing in it; available. */
    EMPTY,
    /** Holds something. */
    FILLED,
    /** Under the mouse (or keyboard focus). */
    HOVER,
    /** The current pick. */
    SELECTED,
    /** Cannot be used right now. */
    DISABLED,
    /** Not unlocked: shown so the player knows it exists. */
    LOCKED,
    /** Something is being dragged over it and it will accept the drop. */
    DROP_OK,
    /** Something is being dragged over it and it will refuse the drop. */
    DROP_BAD,
    /** The thing being dragged came from here. */
    DRAG_SOURCE
}
