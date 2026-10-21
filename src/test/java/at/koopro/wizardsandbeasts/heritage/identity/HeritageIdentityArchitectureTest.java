package at.koopro.wizardsandbeasts.heritage.identity;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTraits;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared heritage-identity layer, without a world: practice pays at the pace it promises and no faster, the
 * Free Elf node waits for freedom, every profile line has words, and the profile survives the wire.
 */
class HeritageIdentityArchitectureTest {

    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");
    private static final Path PROFILES =
            Path.of("src/main/java/at/koopro/wizardsandbeasts/heritage/identity/HeritageProfiles.java");
    private static JsonObject lang;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Reader reader = Files.newBufferedReader(LANG)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    // -- practice --------------------------------------------------------------------------------------

    @Test
    void practicePaysAPointAtTheThreshold() {
        HeritagePracticeData data = HeritagePracticeData.EMPTY;
        int points = 0;
        long day = 0;
        // ELF_SUMMONS is worth 3: under the daily cap of 8, two summonses (6) and a snap (1) count fully.
        while (data.pointsEarned() == 0) {
            HeritagePracticeRules.Outcome out = HeritagePracticeRules.practise(data, PracticeKind.ELF_SUMMONS, day);
            points += out.points();
            data = out.data();
            if (HeritagePracticeRules.leftToday(data, day) == 0) {
                day++;
            }
        }
        assertEquals(1, points);
        assertEquals(1, data.pointsEarned());
        assertTrue(data.progress() < HeritagePracticeRules.PER_POINT);
    }

    @Test
    void theDailyCapHoldsHoweverOftenAnArtIsUsed() {
        HeritagePracticeData data = HeritagePracticeData.EMPTY;
        for (int i = 0; i < 500; i++) {
            data = HeritagePracticeRules.practise(data, PracticeKind.ELF_SNAP, 7L).data();
        }
        assertEquals(HeritagePracticeRules.DAILY_CAP, data.today());
        assertEquals(500, data.count(PracticeKind.ELF_SNAP), "every use is still counted for the sheet");
        assertEquals(0, data.pointsEarned(), "a single day of snapping earned a point");
        assertEquals(0, HeritagePracticeRules.leftToday(data, 7L));
        assertEquals(HeritagePracticeRules.DAILY_CAP, HeritagePracticeRules.leftToday(data, 8L), "a new day resets");
    }

    @Test
    void aPointTakesSeveralDaysOfFullPractice() {
        int days = (int) Math.ceil(HeritagePracticeRules.PER_POINT / (double) HeritagePracticeRules.DAILY_CAP);
        assertTrue(days >= 3, "practice pays faster than a wizard masters spells");
    }

    @Test
    void everyPracticeKindBelongsToAPeopleThatHoldsNoWand() {
        for (PracticeKind kind : PracticeKind.values()) {
            assertFalse(kind.heritage().canUseWand(), kind + " gives a wand-using people a second road to points");
            assertTrue(lang.has(kind.translationKey()), kind.translationKey());
            assertTrue(kind.weight() > 0 && kind.weight() <= HeritagePracticeRules.DAILY_CAP, kind.name());
        }
    }

    // -- node gates --------------------------------------------------------------------------------------

    @Test
    void freedomIsGivenNotBought() {
        PlayerHeritageData bound = new PlayerHeritageData();
        bound.setSelectedHeritage(Heritage.HOUSE_ELF);
        bound.setSelectedHeritageVariant(HeritageVariant.ELF_BOUND);
        PlayerHeritageData hall = new PlayerHeritageData();
        hall.setSelectedHeritage(Heritage.HOUSE_ELF);
        hall.setSelectedHeritageVariant(HeritageVariant.ELF_HALL);
        PlayerHeritageData free = new PlayerHeritageData();
        free.setSelectedHeritage(Heritage.HOUSE_ELF);
        free.setSelectedHeritageVariant(HeritageVariant.ELF_FREE);
        assertFalse(HeritageNodeGates.open("elf_free", bound));
        assertFalse(HeritageNodeGates.open("elf_free", hall));
        assertTrue(HeritageNodeGates.open("elf_free", free));
        assertTrue(HeritageNodeGates.open("elf_devotion", bound), "an ungated node was gated");
    }

    // -- traits and words --------------------------------------------------------------------------------

    @Test
    void everyTraitCutsSomeWay() {
        boolean anyAdvantage = false;
        boolean anyDisadvantage = false;
        for (HeritageTraits.Trait trait : HeritageTraits.all()) {
            anyAdvantage |= trait.polarity().isAdvantage();
            anyDisadvantage |= trait.polarity().isDisadvantage();
            assertTrue(lang.has(trait.getDescriptionKey()), trait.getDescriptionKey());
        }
        assertTrue(anyAdvantage && anyDisadvantage);
        assertEquals(HeritageTraits.Polarity.DISADVANTAGE, HeritageTraits.byId("bound_service").polarity());
        assertEquals(HeritageTraits.Polarity.ADVANTAGE, HeritageTraits.byId("household_magic").polarity());
    }

    @Test
    void everyProfileLineHasWords() throws IOException {
        String source = Files.readString(PROFILES);
        Matcher m = Pattern.compile("K \\+ \"([a-z_.]+)\"").matcher(source);
        List<String> missing = new ArrayList<>();
        int found = 0;
        while (m.find()) {
            found++;
            String key = "heritage.wizards_and_beasts.profile." + m.group(1);
            // "practice." is completed by the heritage id at runtime.
            if (key.endsWith(".")) {
                for (PracticeKind kind : PracticeKind.values()) {
                    String full = key + kind.heritage().getId();
                    if (!lang.has(full)) {
                        missing.add(full);
                    }
                }
            } else if (!lang.has(key)) {
                missing.add(key);
            }
        }
        assertTrue(found > 40, "the key scan found almost nothing; the pattern is stale");
        assertTrue(missing.isEmpty(), "profile lines with no words: " + missing);
        for (HeritageProfile.Section section : HeritageProfile.Section.values()) {
            assertTrue(lang.has(section.translationKey()), section.translationKey());
        }
    }

    // -- the wire ----------------------------------------------------------------------------------------

    @Test
    void theProfileSurvivesTheWire() {
        HeritageProfile profile = new HeritageProfile.Builder()
                .title(Component.literal("House-Elf · Bound"))
                .add(HeritageProfile.Section.SOCIAL, Component.translatable("x.serves", "Sirius"),
                        Component.literal("detail"), HeritageProfile.Tone.MIXED)
                .add(HeritageProfile.Section.STATE, Component.literal("now"), HeritageProfile.Tone.BAD)
                .build();
        ByteBuf buf = Unpooled.buffer();
        HeritageProfile.STREAM_CODEC.encode(buf, profile);
        HeritageProfile back = HeritageProfile.STREAM_CODEC.decode(buf);
        assertEquals(profile.title().getString(), back.title().getString());
        assertEquals(1, back.section(HeritageProfile.Section.SOCIAL).size());
        assertEquals(HeritageProfile.Tone.MIXED, back.section(HeritageProfile.Section.SOCIAL).get(0).tone());
        assertEquals("detail", back.section(HeritageProfile.Section.SOCIAL).get(0).detail().getString());
        assertTrue(back.section(HeritageProfile.Section.ADVANTAGES).isEmpty());
    }

    @Test
    void aFloodOfLinesIsCutAtTheBound() {
        HeritageProfile.Builder b = new HeritageProfile.Builder();
        for (int i = 0; i < HeritageProfile.MAX_LINES * 3; i++) {
            b.add(HeritageProfile.Section.STATE, Component.literal("line " + i), HeritageProfile.Tone.NEUTRAL);
        }
        ByteBuf buf = Unpooled.buffer();
        HeritageProfile.STREAM_CODEC.encode(buf, b.build());
        assertEquals(HeritageProfile.MAX_LINES,
                HeritageProfile.STREAM_CODEC.decode(buf).section(HeritageProfile.Section.STATE).size());
    }
}
