package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.item.broom.BroomPolish;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Broom polish was a tooltip describing behaviour it did not have. These are the assertions that say
 * it now has some — and the one that says it stops having it after twenty minutes, which is the half
 * that is easy to get wrong and impossible to notice.
 */
class BroomPolishTest {

    private static final Path LANG = Path.of("src", "main", "resources", "assets",
            "wizards_and_beasts", "lang", "en_us.json");

    /** A tin restores a share of the broom, so it is worth the same to a Cleansweep and an Oakshaft. */
    @Test
    void repairScalesWithTheBroomRatherThanBeingFlat() {
        ItemStack cheap = new ItemStack(BroomItemRegistry.CLEANSWEEP_SEVEN.get());
        ItemStack dear = new ItemStack(BroomItemRegistry.OAKSHAFT_79.get());

        assertTrue(dear.getMaxDamage() > cheap.getMaxDamage(), "test premise: the Oakshaft lasts longer");
        assertTrue(BroomPolish.repairAmount(dear) > BroomPolish.repairAmount(cheap),
                "a flat repair would be a full service on the cheap broom and a rounding error on "
                        + "the expensive one, which is backwards for a consumable");
        assertEquals(Math.round(dear.getMaxDamage() * BroomPolish.REPAIR_FRACTION),
                BroomPolish.repairAmount(dear));
    }

    /** Servicing a damaged broom repairs it and starts the window. */
    @Test
    void polishingRepairsAndStartsTheWindow() {
        ItemStack broom = new ItemStack(BroomItemRegistry.FIREBOLT.get());
        broom.setDamageValue(broom.getMaxDamage() - 1);

        assertTrue(BroomPolish.apply(broom, 1000L), "a damaged broom must be worth a tin");
        assertEquals(broom.getMaxDamage() - 1 - BroomPolish.repairAmount(broom), broom.getDamageValue());
        assertTrue(BroomPolish.isPolished(broom, 1000L));
    }

    /** Repair never overshoots into negative damage. */
    @Test
    void repairStopsAtPristine() {
        ItemStack broom = new ItemStack(BroomItemRegistry.CLEANSWEEP_SEVEN.get());
        broom.setDamageValue(1);
        BroomPolish.apply(broom, 0L);
        assertEquals(0, broom.getDamageValue());
    }

    /**
     * The window closes. This is the assertion that matters: an absolute expiry that is never checked
     * is a permanent buff wearing a timer's clothes.
     */
    @Test
    void polishWearsOff() {
        ItemStack broom = new ItemStack(BroomItemRegistry.NIMBUS_2000.get());
        BroomPolish.apply(broom, 500L);

        assertTrue(BroomPolish.isPolished(broom, 500L + BroomPolish.DURATION_TICKS - 1));
        assertFalse(BroomPolish.isPolished(broom, 500L + BroomPolish.DURATION_TICKS),
                "the window is exclusive at its far end, so it does close");
        assertFalse(BroomPolish.isPolished(broom, 500L + BroomPolish.DURATION_TICKS * 2));
    }

    /** A sound, already-slick broom is not worth a tin, so the click must not spend one. */
    @Test
    void aBroomThatNeedsNothingDoesNotConsumeATin() {
        ItemStack broom = new ItemStack(BroomItemRegistry.COMET_260.get());
        assertTrue(BroomPolish.apply(broom, 100L), "the first tin buys the window");
        assertFalse(BroomPolish.apply(broom, 200L),
                "a second tin on a sound, still-slick broom must report that it changed nothing");
    }

    /** Polishing again refreshes rather than stacking — two tins are not forty minutes. */
    @Test
    void polishingRefreshesRatherThanStacking() {
        ItemStack broom = new ItemStack(BroomItemRegistry.COMET_260.get());
        BroomPolish.apply(broom, 0L);
        BroomPolish.apply(broom, 100L);
        assertFalse(BroomPolish.isPolished(broom, 100L + BroomPolish.DURATION_TICKS),
                "the window must run from the last tin, not the sum of them");
    }

    /** A broom with nothing left is broken, not slow, and must be refused until it is serviced. */
    @Test
    void aSpentBroomIsRefused() {
        ItemStack broom = new ItemStack(BroomItemRegistry.CLEANSWEEP_SEVEN.get());
        assertFalse(BroomItem.isSpent(broom), "a fresh broom flies");

        broom.setDamageValue(broom.getMaxDamage());
        assertTrue(BroomItem.isSpent(broom), "a fully damaged broom must not be rideable");

        BroomPolish.apply(broom, 0L);
        assertFalse(BroomItem.isSpent(broom), "and polish must be enough to get it flying again");
    }

    /**
     * Every message this feature shows the player must have a line.
     *
     * <p>Asserted here rather than left to {@code LangParityTest}, which scans for a literal
     * {@code Component.translatable("...")} and misses the keys that reach it any other way.
     */
    @Test
    void everyBroomMessageHasALangLine() throws IOException {
        JsonObject lang = new Gson().fromJson(Files.readString(LANG), JsonObject.class);
        for (String key : new String[]{
                "broom.wizards_and_beasts.damage.minor",
                "broom.wizards_and_beasts.damage.severe",
                "broom.wizards_and_beasts.polish.applied",
                "broom.wizards_and_beasts.polish.not_needed",
                "broom.wizards_and_beasts.polish.tooltip",
                "broom.wizards_and_beasts.spent"}) {
            assertTrue(lang.has(key), "en_us.json has no line for " + key
                    + ", so the player would be shown the raw key");
        }
    }
}
