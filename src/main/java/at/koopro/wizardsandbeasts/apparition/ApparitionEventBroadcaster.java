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
 * {@link #NOOP}, so the whole VFX layer can be built against this interface without any of the mechanics
 * code learning that it exists.
 */
@NullMarked
public interface ApparitionEventBroadcaster {

    /** Discards everything. The default until a presentation layer installs itself. */
    ApparitionEventBroadcaster NOOP = new ApparitionEventBroadcaster() {};

    /** An attempt began. {@code phase} is always {@link ApparitionPhase#DETERMINATION} here. */
    default void onChargeBegin(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase) {}

    /** The attempt moved between Determination and Deliberation. */
    default void onPhaseChange(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase) {}

    /**
     * An attempt resolved.
     *
     * @param destination where they ended up, or {@code null} when a catastrophe kept them at the origin
     */
    default void onResolved(ServerPlayer player, ApparitionTier tier, SplinchTier splinchTier,
                            Vec3 origin, @Nullable Vec3 destination) {}

    /** Something of the player's was torn loose and left at {@code origin}. */
    default void onResidue(ServerPlayer player, Vec3 origin, SplinchTier splinchTier) {}
}
