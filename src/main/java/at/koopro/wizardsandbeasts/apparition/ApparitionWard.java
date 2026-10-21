package at.koopro.wizardsandbeasts.apparition;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public record ApparitionWard(
        Identifier wardId,
        Identifier dimensionId,
        AABB bounds,
        Component blockMessage,
        boolean allowAdmins,
        boolean blocksFloo
) {
    /**
     * A ward that also refuses Floo arrivals — the default. A place warded against Apparating in is warded against
     * arriving by fire too: Umbridge's watch on the Hogwarts grates, the Ministry's on its own. An operator can open
     * the hearths of a ward by saving it with {@code "blocksFloo": false}.
     */
    public ApparitionWard(Identifier wardId, Identifier dimensionId, AABB bounds, Component blockMessage,
                          boolean allowAdmins) {
        this(wardId, dimensionId, bounds, blockMessage, allowAdmins, true);
    }
}
