package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Everything the presentation layer needs to know about an Apparition, and nothing it can influence.
 *
 * <p>The server calls this; it never waits on it and never reads anything back. The shipped implementation is
 * {@link #NOOP}, so every mechanic behaves identically whether or not anything is listening.
 *
 * <p>Every parameter here is a <b>decision</b>, not an input. {@code radius} and {@code crackVariant} arrive
 * already resolved precisely so that proficiency (a private stat) and true heritage (which disguises exist to
 * hide) never reach an observer's client.
 */
@NullMarked
public interface ApparitionEventBroadcaster {

    /** Discards everything. The default until a presentation layer installs itself. */
    ApparitionEventBroadcaster NOOP = new ApparitionEventBroadcaster() {};

    /**
     * An attempt began. {@code phase} is always {@link ApparitionPhase#DETERMINATION} here.
     *
     * @param windowOpen  tick the Deliberation window opens on
     * @param windowClose last tick a release still counts
     */
    default void onChargeBegin(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                               int windowOpen, int windowClose) {}

    /**
     * One tick of an attempt in flight, or a transition between Determination and Deliberation.
     *
     * <p>Sent every tick rather than only on transitions: the ring has to agree with the server's clock to
     * within a tick, and a client counting locally between transitions is exactly the drift this replaces.
     *
     * @param elapsed ticks since the charge began
     */
    default void onChargeTick(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                              int elapsed, int windowOpen, int windowClose) {}

    /** The attempt moved between Determination and Deliberation. */
    default void onPhaseChange(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                               int elapsed, int windowOpen, int windowClose) {}

    /**
     * An attempt resolved.
     *
     * @param destination  where they ended up, or {@code null} when a catastrophe kept them at the origin
     * @param radius       how far the crack carries, in blocks. Already scaled; never zero
     * @param crackVariant which crack, already resolved against the caster's <i>apparent</i> form
     */
    default void onResolved(ServerPlayer player, ApparitionTier tier, SplinchTier splinchTier,
                            Vec3 origin, @Nullable Vec3 destination,
                            int radius, ApparitionCrackVariant crackVariant) {}

    /** Something of the player's was torn loose and left at {@code origin}. */
    default void onResidue(ServerPlayer player, Vec3 origin, SplinchTier splinchTier) {}
}
