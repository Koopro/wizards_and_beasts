package at.koopro.wizardsandbeasts.apparition;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public record ApparitionWard(
        Identifier wardId,
        Identifier dimensionId,
        AABB bounds,
        Component blockMessage,
        boolean allowAdmins
) {
}
