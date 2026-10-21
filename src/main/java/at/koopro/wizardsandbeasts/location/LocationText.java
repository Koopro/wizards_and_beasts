package at.koopro.wizardsandbeasts.location;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Lang keys for locations, derived from ids so a datapack adds a place, tier, contract or service with lang lines
 * and nothing else: {@code location.<ns>.<location>.name}, {@code .tier.<id>}, {@code .contract.<id>} (+
 * {@code .brief}), and {@code location.<ns>.service.<service>} for a service, shared by every place offering it.
 */
@NullMarked
public final class LocationText {

    private LocationText() {}

    private static String base(Identifier location) {
        return "location." + location.getNamespace() + "." + location.getPath();
    }

    public static Component name(Identifier location) {
        return Component.translatable(base(location) + ".name");
    }

    public static Component tier(Identifier location, String tierId) {
        return Component.translatable(base(location) + ".tier." + tierId);
    }

    public static Component contractTitle(Identifier location, String contractId) {
        return Component.translatable(base(location) + ".contract." + contractId);
    }

    public static Component contractBrief(Identifier location, String contractId) {
        return Component.translatable(base(location) + ".contract." + contractId + ".brief");
    }

    public static Component service(Identifier service) {
        return Component.translatable("location." + service.getNamespace() + ".service." + service.getPath());
    }
}
