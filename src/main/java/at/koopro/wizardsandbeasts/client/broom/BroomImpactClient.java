package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.network.broom.BroomImpactC2SPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Sends the rider's own crash report. Client-only, reached from {@code BroomEntity} through
 * {@link at.koopro.wizardsandbeasts.util.ClientClassBridge} so the entity class stays dist-shared.
 *
 * <p>Reflection for something on the collision path is affordable because a collision worth reporting is
 * rare — {@code BroomEntity#reportImpact} filters out everything below the minor threshold and every
 * landing it has already announced, which is what the ground-skimming case would otherwise be every tick.
 */
@NullMarked
public final class BroomImpactClient {

    private BroomImpactClient() {}

    public static void report(BroomEntity broom, boolean gentle, float severity) {
        ClientPacketDistributor.sendToServer(
                new BroomImpactC2SPayload(broom.getId(), severity, gentle));
    }
}
