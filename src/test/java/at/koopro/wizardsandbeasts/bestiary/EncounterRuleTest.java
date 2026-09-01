package at.koopro.wizardsandbeasts.bestiary;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bestiary unlock rule, tested on its own. {@link EncounterRule} is deliberately free of player,
 * level and registry so this needs no Minecraft bootstrap — the rule that used to live inline in
 * {@code BestiaryDiscoveryHandler} could only be checked by playing the game.
 */
class EncounterRuleTest {

    // -- sighting -------------------------------------------------------------------------------

    @Test
    void sighting_opensAnUnopenedEntry() {
        assertEquals(DiscoveryTier.SIGHTED, EncounterRule.onSighted(DiscoveryTier.UNDISCOVERED));
    }

    @Test
    void sighting_neverAdvancesAnAlreadyOpenEntry() {
        // Standing next to a bowtruckle for a minute must not master it.
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            if (tier == DiscoveryTier.UNDISCOVERED) continue;
            assertEquals(tier, EncounterRule.onSighted(tier),
                    tier + " should be unchanged by a repeat sighting");
        }
    }

    // -- declared trigger -----------------------------------------------------------------------

    @Test
    void trigger_advancesExactlyOneTier() {
        assertEquals(DiscoveryTier.SIGHTED, EncounterRule.onTriggered(DiscoveryTier.UNDISCOVERED));
        assertEquals(DiscoveryTier.ENCOUNTERED, EncounterRule.onTriggered(DiscoveryTier.SIGHTED));
        assertEquals(DiscoveryTier.STUDIED, EncounterRule.onTriggered(DiscoveryTier.ENCOUNTERED));
        assertEquals(DiscoveryTier.MASTERED, EncounterRule.onTriggered(DiscoveryTier.STUDIED));
    }

    @Test
    void trigger_clampsAtMastered() {
        assertEquals(DiscoveryTier.MASTERED, EncounterRule.onTriggered(DiscoveryTier.MASTERED));
    }

    @Test
    void everyTransitionIsMonotonic() {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            assertTrue(EncounterRule.onSighted(tier).ordinal() >= tier.ordinal());
            assertTrue(EncounterRule.onTriggered(tier).ordinal() >= tier.ordinal());
        }
    }

    // -- channel matching -----------------------------------------------------------------------

    @Test
    void declaredTriggerDeepensOnlyOnItsOwnChannel() {
        assertTrue(EncounterRule.deepensOn(EncounterTrigger.KILL, EncounterTrigger.KILL));
        assertFalse(EncounterRule.deepensOn(EncounterTrigger.KILL, EncounterTrigger.LOOT));
        assertTrue(EncounterRule.deepensOn(EncounterTrigger.LOOT, EncounterTrigger.LOOT));
        assertFalse(EncounterRule.deepensOn(EncounterTrigger.PROXIMITY, EncounterTrigger.KILL));
    }

    @Test
    void manualEntriesAreNeverDeepenedByWorldEvents() {
        for (EncounterTrigger event : EncounterTrigger.values()) {
            assertFalse(EncounterRule.deepensOn(EncounterTrigger.MANUAL, event),
                    "MANUAL entries advance only by command, not by " + event);
        }
    }

    // -- the composite the handler applies ------------------------------------------------------

    @Test
    void firstKillOfAKillEntry_landsOnEncountered() {
        // A kill proves a sighting AND fires the KILL channel, so an unopened KILL entry jumps two.
        DiscoveryTier after = EncounterRule.onTriggered(EncounterRule.onSighted(DiscoveryTier.UNDISCOVERED));
        assertEquals(DiscoveryTier.ENCOUNTERED, after);
    }

    @Test
    void repeatKills_keepClimbingInsteadOfFlipFlopping() {
        // The shape this replaces computed `old == SIGHTED ? ENCOUNTERED : SIGHTED`, which asked for a
        // downgrade on every kill after the second and so could never reach STUDIED.
        DiscoveryTier tier = DiscoveryTier.UNDISCOVERED;
        for (int i = 0; i < 5; i++) {
            tier = EncounterRule.onTriggered(EncounterRule.onSighted(tier));
        }
        assertEquals(DiscoveryTier.MASTERED, tier);
    }

    @Test
    void seeingAKillEntryIsEnoughToOpenIt() {
        // The regression that motivated the rule: a KILL-triggered Unicorn used to be invisible to the
        // sighting scan, so its page could only be opened by killing one.
        assertEquals(DiscoveryTier.SIGHTED, EncounterRule.onSighted(DiscoveryTier.UNDISCOVERED));
        assertFalse(EncounterRule.deepensOn(EncounterTrigger.KILL, EncounterTrigger.PROXIMITY),
                "sighting still must not deepen a KILL entry past SIGHTED");
    }

    // -- copy contract --------------------------------------------------------------------------

    /**
     * Both strings a tier contributes to the screen must be translation keys.
     *
     * <p>{@code unlockHint()} shipped as a hardcoded English sentence, which is why this is asserted
     * rather than assumed: it is the first copy a new player reads and it was the one Bestiary string
     * no language file could reach.
     */
    @Test
    void everyTierNamesTranslationKeysRatherThanEnglish() {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            String slug = tier.name().toLowerCase(Locale.ROOT);
            assertEquals("bestiary.wizards_and_beasts.tier." + slug, keyOf(tier.displayName()));
            assertEquals("bestiary.wizards_and_beasts.tier." + slug + ".hint", keyOf(tier.unlockHint()));
        }
    }

    private static String keyOf(Component component) {
        ComponentContents contents = component.getContents();
        assertInstanceOf(TranslatableContents.class, contents,
                "expected a translatable component, got " + contents.getClass().getSimpleName());
        return ((TranslatableContents) contents).getKey();
    }
}
