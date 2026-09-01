package at.koopro.wizardsandbeasts.floo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tightened address rules, and what ownership means on an entry.
 *
 * <p>Separate from {@link FlooAddressTest} rather than folded into it: that suite pins the rules the
 * validator was born with — blank, length, no formatting codes — and this one pins the ones added
 * when addresses became things players type into a field and hearths became things players own.
 */
class FlooAddressRulesTest {

    // ── the character allow-list ────────────────────────────────────────────────────────────

    @Test
    void placeNamesWithPunctuationAndDigitsPass() {
        for (String address : List.of(
                "12 Grimmauld Place",
                "Weasleys' Wizarding Wheezes",
                "Stoatshead Hill",
                "Ottery St Catchpole",
                "Spinner's End",
                "Number-Four Privet Drive")) {
            assertTrue(FlooAddress.isValid(address), address + " should be legal");
        }
    }

    @Test
    void punctuationOutsideTheAllowListIsRefused() {
        // An allow-list is what makes this finishable. Each of these is a character a place name has
        // no use for and a list row, a chat quote or a command argument has an opinion about.
        for (String address : List.of(
                "Diagon Alley!",
                "Diagon, Alley",
                "Diagon.Alley",
                "Diagon/Alley",
                "Diagon\"Alley",
                "Diagon@Alley",
                "Diagon_Alley")) {
            assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS, FlooAddress.validate(address),
                    address + " should be refused");
        }
    }

    @Test
    void theSectionSignIsStillRefusedAsAConsequenceOfTheAllowList() {
        // It used to be named explicitly. It is now simply not on the list, which is the point: the
        // rule no longer depends on anyone having thought of this particular character.
        assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS, FlooAddress.validate("§cKnockturn"));
    }

    @Test
    void invisibleAndDirectionalCharactersAreRefused() {
        // The reason an allow-list was worth the change. A zero-width joiner makes two different
        // addresses render identically; a bidi override reverses the rest of the row it sits in.
        assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS, FlooAddress.validate("Diagon‍Alley"));
        assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS, FlooAddress.validate("Diagon‮Alley"));
        assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS, FlooAddress.validate("Diagon﻿Alley"));
    }

    @Test
    void theTypographicApostropheIsRefused() {
        // Deliberate. Allowing both would let "Weasleys' " and "Weasleys’ " key differently while
        // rendering almost identically - one findable, one not, and nothing on screen to say which.
        assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS,
                FlooAddress.validate("Weasleys’ Wizarding Wheezes"));
        assertTrue(FlooAddress.isValid("Weasleys' Wizarding Wheezes"));
    }

    // ── length ─────────────────────────────────────────────────────────────────────────────

    @Test
    void theMinimumIsInclusive() {
        assertTrue(FlooAddress.isValid("a".repeat(FlooAddress.MIN_LENGTH)));
        assertEquals(FlooAddress.Validity.TOO_SHORT,
                FlooAddress.validate("a".repeat(FlooAddress.MIN_LENGTH - 1)));
    }

    @Test
    void theBoundsAreThreeToFortyEight() {
        assertEquals(3, FlooAddress.MIN_LENGTH);
        assertEquals(48, FlooAddress.MAX_LENGTH);
    }

    @Test
    void shortnessIsMeasuredAfterStripping() {
        // "  a  " is a one-character address wearing padding, not a five-character one.
        assertEquals(FlooAddress.Validity.TOO_SHORT, FlooAddress.validate("  a  "));
    }

    // ── a name, not a number ───────────────────────────────────────────────────────────────

    @Test
    void purelyNumericAddressesAreRefused() {
        for (String address : List.of("12345", "12 34", "1-2-3", "007")) {
            assertEquals(FlooAddress.Validity.NO_LETTERS, FlooAddress.validate(address),
                    address + " should be refused as numberless");
        }
    }

    @Test
    void aSingleLetterIsEnoughToMakeItAName() {
        assertTrue(FlooAddress.isValid("12a"));
    }

    @Test
    void anUnpronounceableStringIsReportedAsIllegalNotAsNumberless() {
        // Both rules are broken. The character rule is the harder problem and the more useful thing
        // to be told, so it wins - otherwise a player fixing "no letters" would add one and hit a
        // second refusal they were never warned about.
        assertEquals(FlooAddress.Validity.ILLEGAL_CHARACTERS, FlooAddress.validate("!!!"));
    }

    // ── ownership on the entry ─────────────────────────────────────────────────────────────

    private static FlooRegistryEntry entry(Optional<UUID> owner, boolean isPublic) {
        return new FlooRegistryEntry("The Burrow",
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO, true, isPublic, owner);
    }

    @Test
    void anOwnedEntryMatchesOnlyItsOwner() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(entry(Optional.of(owner), false).isOwnedBy(owner));
        assertFalse(entry(Optional.of(owner), false).isOwnedBy(other));
    }

    @Test
    void anUnownedEntryIsOwnedByNobody() {
        // Not "owned by everybody". mayAdminister decides that an ownerless hearth is editable, and
        // it does so with an explicit clause - this predicate must not smuggle the same decision in
        // by answering true, or an ownerless private hearth would become reachable by anyone.
        assertFalse(entry(Optional.empty(), false).isOwnedBy(UUID.randomUUID()));
    }

    @Test
    void withPublicKeepsTheOwnerAndTheRest() {
        UUID owner = UUID.randomUUID();
        FlooRegistryEntry original = entry(Optional.of(owner), false);
        FlooRegistryEntry published = original.withPublic(true);

        assertTrue(published.isPublic());
        assertTrue(published.isOwnedBy(owner), "publishing a hearth must not orphan it");
        assertEquals(original.networkAddress(), published.networkAddress());
        assertEquals(original.blockPos(), published.blockPos());
        assertEquals(original.isEnabled(), published.isEnabled());
    }

    @Test
    void withEnabledKeepsTheOwnerAndTheVisibility() {
        UUID owner = UUID.randomUUID();
        FlooRegistryEntry sealed = entry(Optional.of(owner), false).withEnabled(false);

        assertFalse(sealed.isEnabled());
        assertFalse(sealed.isPublic(), "sealing a line must not publish it");
        assertTrue(sealed.isOwnedBy(owner), "sealing a line must not orphan it");
    }

    @Test
    void theUnownedFactoryProducesNoOwner() {
        FlooRegistryEntry made = FlooRegistryEntry.unowned("The Burrow",
                Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO, true);
        assertTrue(made.owner().isEmpty());
        assertTrue(made.isEnabled());
        assertTrue(made.isPublic());
    }
}
