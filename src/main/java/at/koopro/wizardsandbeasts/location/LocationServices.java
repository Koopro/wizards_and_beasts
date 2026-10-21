package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.gringotts.GringottsServices;
import at.koopro.wizardsandbeasts.location.ollivanders.OllivanderServices;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every service behaviour, by id. Fixed at class load: a service is code, and which location offers it is data.
 * {@link Locations.Loader} refuses a location naming a service missing here.
 */
@NullMarked
public final class LocationServices {

    private static final Map<Identifier, LocationService> SERVICES = new LinkedHashMap<>();

    static {
        GringottsServices.register(LocationServices::register);
        OllivanderServices.register(LocationServices::register);
        at.koopro.wizardsandbeasts.location.apothecary.ApothecaryServices.register(LocationServices::register);
        at.koopro.wizardsandbeasts.location.knockturn.FenceServices.register(LocationServices::register);
        at.koopro.wizardsandbeasts.ministry.licence.LicenceOffice.register(LocationServices::register);
        at.koopro.wizardsandbeasts.education.LessonServices.register(LocationServices::register);
        at.koopro.wizardsandbeasts.travel.TravelServices.register(LocationServices::register);
    }

    private LocationServices() {}

    private static void register(String path, LocationService service) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
        if (SERVICES.putIfAbsent(id, service) != null) {
            throw new IllegalStateException("duplicate location service " + id);
        }
    }

    public static @Nullable LocationService get(Identifier id) {
        return SERVICES.get(id);
    }

    public static Map<Identifier, LocationService> all() {
        return java.util.Collections.unmodifiableMap(SERVICES);
    }

    /** How a feature hands its services over. */
    @FunctionalInterface
    public interface Sink {
        void register(String path, LocationService service);
    }
}
