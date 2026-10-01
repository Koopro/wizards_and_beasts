package at.koopro.wizardsandbeasts.spell.patronus;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** A Patronus belongs to the person: stable for one character, spread across people, blind to lineage. */
class PatronusFormDeterminerTest {

    @Test
    void theSameCharacterAlwaysConjuresTheSameAnimal() {
        UUID id = UUID.fromString("3f2b8d1c-0000-4000-8000-00000000abcd");
        Identifier first = PatronusFormDeterminer.determine(id);
        assertNotNull(first);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, PatronusFormDeterminer.determine(id));
        }
    }

    @Test
    void differentPeopleConjureEveryAnimalBetweenThem() {
        Random random = new Random(42);
        Set<Identifier> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(PatronusFormDeterminer.determine(new UUID(random.nextLong(), random.nextLong())));
        }
        assertEquals(new HashSet<>(PatronusFormDeterminer.FORMS), seen, "some animal is never anyone's Patronus");
    }
}
