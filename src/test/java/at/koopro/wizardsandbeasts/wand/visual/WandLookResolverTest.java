package at.koopro.wizardsandbeasts.wand.visual;

import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.registry.WandRareConstruction;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodAppearance;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandCastModifiers;
import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The look channels as pure functions: what each input does to a wand's look, and the properties the
 * renderer relies on (bounded, monotonic, deterministic, and nothing at all for the Elder Wand).
 */
class WandLookResolverTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    private static WandWoodDefinition wood(WandWoodAppearance appearance) {
        return new WandWoodDefinition(Component.literal("test"), List.of(), Map.of(), List.of(), "common", 0.2f,
                WandCastModifiers.NEUTRAL, WandTemperament.NEUTRAL, appearance);
    }

    private static WandLook resolve(float inches, WandFlexibility flex) {
        return WandLookResolver.resolve(null, id("ash"), null, id("phoenix_feather"), inches, flex, null, false);
    }

    // ── length ───────────────────────────────────────────────────────────────

    @Test
    void longerWandsAreDrawnLongerAndTheModelLengthIsTheReference() {
        assertEquals(1.0f, WandLookResolver.lengthScale(WandLookResolver.REFERENCE_INCHES), 1e-6f);
        float previous = 0.0f;
        for (float inches = 5.0f; inches <= 20.0f; inches += 0.25f) {
            float scale = WandLookResolver.lengthScale(inches);
            assertTrue(scale >= previous, "length scale must not shrink as the wand grows: " + inches);
            assertTrue(scale >= WandLookResolver.MIN_LENGTH_SCALE && scale <= WandLookResolver.MAX_LENGTH_SCALE);
            previous = scale;
        }
        // The recipes' own range must read as different wands, not be clamped flat.
        assertTrue(WandLookResolver.lengthScale(15.0f) - WandLookResolver.lengthScale(8.5f) > 0.5f);
    }

    @Test
    void anUnknownOrBrokenLengthDrawsTheModelAsBuilt() {
        assertEquals(1.0f, WandLookResolver.lengthScale(null));
        assertEquals(1.0f, WandLookResolver.lengthScale(Float.NaN));
        assertEquals(1.0f, WandLookResolver.lengthScale(Float.POSITIVE_INFINITY));
    }

    // ── flexibility ──────────────────────────────────────────────────────────

    @Test
    void suppleWandsAreSlimmerAndMoreBowedAndUnyieldingOnesAreStraight() {
        float girth = 2.0f;
        float bow = -1.0f;
        for (WandFlexibility flex : WandFlexibility.values()) {
            assertTrue(WandLookResolver.girth(flex) <= girth, flex + " must not be thicker than a stiffer wand");
            assertTrue(WandLookResolver.bowDegrees(flex) > bow || flex == WandFlexibility.UNYIELDING,
                    flex + " must bow more than a stiffer wand");
            assertTrue(WandLookResolver.girth(flex) <= 1.0f, "a shaft never outgrows its joint with the grip");
            girth = WandLookResolver.girth(flex);
            bow = WandLookResolver.bowDegrees(flex);
        }
        assertEquals(0.0f, WandLookResolver.bowDegrees(WandFlexibility.UNYIELDING));
        assertEquals(0.0f, resolve(12.0f, WandFlexibility.UNYIELDING).bowDegrees());
        assertEquals(WandLookResolver.bowDegrees(WandFlexibility.SPRINGY),
                Math.abs(resolve(12.0f, WandFlexibility.SPRINGY).bowDegrees()), 1e-6f);
    }

    // ── determinism and variation ────────────────────────────────────────────

    @Test
    void theSameMakeAlwaysLooksTheSame() {
        WandLook first = resolve(12.37f, WandFlexibility.SUPPLE);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, resolve(12.37f, WandFlexibility.SUPPLE));
        }
    }

    /**
     * The seed is built from String hashes and float bits, which are fixed by the language. If this
     * number changes, every crafted wand in every world turns a little differently and some gain or lose
     * a rare construction: change it only on purpose.
     */
    @Test
    void theSeedIsPinned() {
        assertEquals(-1583759333217L,
                WandLookResolver.seed(id("ash"), id("phoenix_feather"), 12.5f, WandFlexibility.PLIANT));
    }

    @Test
    void handTurnedWandsDifferSlightlyAndStayWithinBounds() {
        int distinctTwists = 0;
        float lastTwist = Float.NaN;
        for (int i = 0; i < 200; i++) {
            WandLook look = resolve(10.0f + i * 0.013f, WandFlexibility.PLIANT);
            assertTrue(Math.abs(look.twistDegrees()) <= WandLookResolver.MAX_TWIST_DEGREES);
            assertTrue(look.phase() >= 0.0f && look.phase() < 1.0f);
            if (look.twistDegrees() != lastTwist) {
                distinctTwists++;
            }
            lastTwist = look.twistDegrees();
            int base = WandAppearance.woodTint(null, id("ash"));
            for (int shift : new int[]{16, 8, 0}) {
                int a = (base >> shift) & 0xFF;
                int b = (look.tint() >> shift) & 0xFF;
                assertTrue(Math.abs(a - b) <= Math.ceil(a * WandLookResolver.TINT_VARIATION) + 1,
                        "tint strays too far from the wood's colour");
            }
        }
        assertTrue(distinctTwists > 150, "two crafted wands should rarely sit exactly alike");
    }

    @Test
    void unitDrawsAreUniformEnough() {
        int[] buckets = new int[10];
        for (long s = 0; s < 20_000; s++) {
            float u = WandLookResolver.unit(s * 7919L, 3);
            assertTrue(u >= 0.0f && u < 1.0f);
            buckets[(int) (u * 10)]++;
        }
        for (int b : buckets) {
            assertTrue(b > 1700 && b < 2300, "bucket " + b + " is off a uniform 2000");
        }
    }

    // ── rare constructions ───────────────────────────────────────────────────

    @Test
    void rareConstructionsTakeTheirShareAndLayOverTheWood() {
        WandWoodAppearance appearance = new WandWoodAppearance(Optional.empty(), Optional.of(id("smooth")),
                Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(new WandRareConstruction(0.10f, Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(id("metal_band"))),
                        new WandRareConstruction(0.05f, Optional.of(id("wrapped")), Optional.empty(),
                                Optional.empty(), Optional.empty())));
        WandWoodDefinition def = wood(appearance);
        int[] counts = new int[3];
        for (int i = 0; i < 20_000; i++) {
            int rare = WandAppearance.rareIndex(def, WandLookResolver.seed(id("ash"), id("core"), 9.0f + i * 1.0e-3f,
                    WandFlexibility.PLIANT));
            counts[rare + 1]++;
        }
        assertEquals(0.85, counts[0] / 20_000.0, 0.02);
        assertEquals(0.10, counts[1] / 20_000.0, 0.015);
        assertEquals(0.05, counts[2] / 20_000.0, 0.01);

        WandConfiguration banded = WandAppearance.configurationFor(def, 0);
        assertEquals(Optional.of(id("smooth")), banded.getModule(WandSlot.HANDLE), "a construction keeps what it does not name");
        assertEquals(Optional.of(id("metal_band")), banded.getModule(WandSlot.ORNAMENT));
        assertEquals(Optional.of(id("wrapped")), WandAppearance.configurationFor(def, 1).getModule(WandSlot.HANDLE));
        assertEquals(Optional.empty(), WandAppearance.configurationFor(def, -1).getModule(WandSlot.ORNAMENT));
    }

    @Test
    void anExplicitConfigurationWinsOverEverythingTheWoodSays() {
        WandConfiguration explicit = WandConfiguration.DEFAULT.withModule(WandSlot.TIP, id("hook"));
        WandLook look = WandLookResolver.resolve(null, id("ash"), null, null, 12.0f, WandFlexibility.PLIANT,
                explicit, false);
        assertEquals(explicit, look.configuration());
        assertEquals(-1, look.rareIndex());
    }

    // ── core light and bond ──────────────────────────────────────────────────

    @Test
    void aWandWithNoCoreHasNoLight() {
        WandLook look = WandLookResolver.resolve(null, id("ash"), null, null, 12.0f, WandFlexibility.PLIANT, null, false);
        assertEquals(0.0f, look.glintStrength());
        for (WandBondLook bond : WandBondLook.values()) {
            assertEquals(0xFF000000, look.glintArgb(bond, 3.0f), "a coreless wand must draw no light at all");
        }
    }

    @Test
    void anUnauthoredCoreStillGetsAQuietStableLight() {
        WandLook a = WandLookResolver.resolve(null, id("ash"), null, id("kraken_ink"), 12.0f, null, null, false);
        WandLook b = WandLookResolver.resolve(null, id("ash"), null, id("kraken_ink"), 12.0f, null, null, false);
        assertEquals(a.glint(), b.glint());
        assertNotEquals(0, a.glint());
        assertTrue(a.glintStrength() <= 0.5f, "an unknown core must not outshine an authored one");
    }

    @Test
    void theDeeperTheBondTheBrighterTheLight() {
        float previous = 0.0f;
        for (WandBondState state : WandBondState.values()) {
            float strength = WandBondLook.of(state).strength();
            assertTrue(strength >= previous, state + " must not be dimmer than a weaker bond");
            previous = strength;
        }
        assertFalse(WandBondLook.of(WandBondState.ACCEPTING).reachesPommel());
        assertTrue(WandBondLook.of(WandBondState.LOYAL).reachesPommel());
        assertTrue(WandBondLook.UNFAMILIAR.saturation() < 0.5f, "a foreign wand's light goes grey");
    }

    @Test
    void theBondIsReadAgainstWhoeverHoldsTheWand() {
        UUID me = UUID.randomUUID();
        UUID them = UUID.randomUUID();
        assertEquals(WandBondLook.DORMANT, WandBondLook.of(Optional.empty(), 1.0f, me));
        assertEquals(WandBondLook.DORMANT, WandBondLook.of(Optional.empty(), 1.0f, null));
        assertEquals(WandBondLook.AWAITING, WandBondLook.of(Optional.of(me), 1.0f, null));
        assertEquals(WandBondLook.UNFAMILIAR, WandBondLook.of(Optional.of(them), 1.0f, me));
        assertEquals(WandBondLook.MASTERED, WandBondLook.of(Optional.of(me), 1.0f, me));
        assertEquals(WandBondLook.RELUCTANT, WandBondLook.of(Optional.of(me), 0.1f, me));
    }

    @Test
    void everyPatternVariesTheLightButNeverPutsItOut() {
        for (WandGlintPattern pattern : WandGlintPattern.values()) {
            float min = 1.0f;
            float max = 0.0f;
            for (int i = 0; i < 2000; i++) {
                float v = pattern.intensity(i * 0.017f, 0.3f);
                assertTrue(v >= 0.35f && v <= 1.0f, pattern + " left [0.35, 1]: " + v);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (pattern != WandGlintPattern.STEADY) {
                assertTrue(max - min > 0.15f, pattern + " barely moves");
            }
        }
    }

    // ── the Elder Wand ───────────────────────────────────────────────────────

    @Test
    void theElderWandIsNeverReshapedOrTinted() {
        WandLook elder = WandLookResolver.resolve(null, id("elder"), null, id("thestral_tail_hair"), 9.0f,
                WandFlexibility.SPRINGY, WandConfiguration.DEFAULT.withModule(WandSlot.TIP, id("hook")), true);
        assertTrue(elder.elder());
        assertEquals(WandAppearance.UNTINTED, elder.tint());
        assertEquals(1.0f, elder.lengthScale());
        assertEquals(1.0f, elder.girth());
        assertEquals(0.0f, elder.bowDegrees());
        assertEquals(0.0f, elder.twistDegrees());
        assertNull(elder.grain());
        assertEquals(WandLook.MODEL_LENGTH, elder.drawnLength());
        assertEquals(WandLookResolver.ELDER_GLINT, elder.glint());
    }
}
