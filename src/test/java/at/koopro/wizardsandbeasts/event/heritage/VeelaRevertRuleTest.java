package at.koopro.wizardsandbeasts.event.heritage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A Veela's chosen harpy form stays; only a change fury forced reverts on recovery (Beast Renaissance D17). */
class VeelaRevertRuleTest {

    @Test
    void aChosenFormStaysAtFullHealth() {
        assertFalse(VeelaHeritageHandler.revertsOnRecovery(false, 1.0f));
    }

    @Test
    void aFuryFormRevertsOnceCalm() {
        assertTrue(VeelaHeritageHandler.revertsOnRecovery(true, 0.9f));
        assertFalse(VeelaHeritageHandler.revertsOnRecovery(true, 0.4f), "still hurt, still furious");
    }
}
