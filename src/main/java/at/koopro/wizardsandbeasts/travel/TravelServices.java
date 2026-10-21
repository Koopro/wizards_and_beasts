package at.koopro.wizardsandbeasts.travel;

import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.travel.courier.CourierService;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialService;
import org.jspecify.annotations.NullMarked;

/** The location services travel adds: courier dispatch and broom time trials. Which places offer them is data. */
@NullMarked
public final class TravelServices {

    private TravelServices() {}

    public static void register(LocationServices.Sink sink) {
        CourierService.register(sink);
        TimeTrialService.register(sink);
        at.koopro.wizardsandbeasts.apparition.licence.ApparitionTestService.register(sink);
    }
}
