package at.koopro.wizardsandbeasts.client.spell.state;

import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientSpellTeacherState {
    private static final List<SpellLearningService.SpellOffer> OFFERS = new ArrayList<>();

    private ClientSpellTeacherState() {}

    public static void setOffers(List<SpellLearningService.SpellOffer> offers) {
        OFFERS.clear();
        OFFERS.addAll(offers);
    }

    public static List<SpellLearningService.SpellOffer> offers() {
        return Collections.unmodifiableList(OFFERS);
    }
}
