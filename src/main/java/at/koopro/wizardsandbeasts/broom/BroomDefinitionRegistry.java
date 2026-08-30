package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class BroomDefinitionRegistry {
    private static final Identifier FALLBACK_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom");

    /**
     * The broom every unresolved id degrades to, held in code rather than read from disk.
     *
     * <p>{@code broom_definitions/broom.json} ships the same values, and {@link #getFallback()}
     * prefers the loaded copy so a datapack can retune the starter broom. This constant is what
     * remains when a datapack deletes that file: without it, every generic broom render and every
     * broom entity tick would hit a missing key, and the only thing left to do was throw — turning
     * a cosmetic datapack removal into a crash.
     *
     * <p>Values are Cleansweep Seven's verbatim, because that is what the generic broom resolved to
     * before it had a definition of its own. Nothing about how the starter broom flies changes.
     */
    /**
     * The generic broom's handle colour, kept in step with {@code broom_definitions/broom.json}.
     *
     * <p>The generic broom is the one broom that draws the shared greyscale sheet rather than a
     * painted one of its own, so without a tint its handle renders grey. It is also the fallback a
     * datapack broom lands on, which makes it the worked example of the no-art path.
     * {@code BroomDefinitionCodecTest} holds this against the JSON.
     */
    private static final int GENERIC_WOOD_TINT = 0xFFA87C4A;

    /**
     * The starter broom's audio, kept in step with {@code broom_definitions/broom.json}.
     *
     * <p>Named here rather than left empty because the constant has to describe the same broom the
     * JSON does — {@code BroomDefinitionCodecTest} compares the two field by field, and a fallback
     * that sounds different from the file it replaces is exactly the tell that gives away which one
     * you are flying.
     */
    private static final BroomAudio GENERIC_AUDIO = new BroomAudio(
            java.util.Optional.of(Identifier.fromNamespaceAndPath(
                    WizardsAndBeastsMod.MODID, "broom_boost_school")),
            java.util.Optional.of(Identifier.fromNamespaceAndPath(
                    WizardsAndBeastsMod.MODID, "broom_wind_slow")),
            java.util.Optional.of(Identifier.fromNamespaceAndPath(
                    WizardsAndBeastsMod.MODID, "broom_trail_dust")));

    private static final BroomDefinition CODE_DEFAULT = new BroomDefinition(
            FALLBACK_ID,
            Component.translatable("item.wizards_and_beasts.broom"),
            BroomTier.SCHOOL,
            0.35f,   // maxSpeed
            0.090f,  // acceleration
            0.012f,  // deceleration
            1.3f,    // boostMultiplier
            40,      // boostDurationTicks
            200,     // boostCooldownTicks
            0.012f,  // weakGravity
            0.10f,   // lerpFactor
            0.75f,   // turnSpeed
            0.14f,   // ascentSpeed
            0.18f,   // descentSpeed
            0.40f,   // handlingRating
            0.80f,   // stabilityRating
            120,     // durability
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", "planks")),
            List.of(),
            BroomSlot.defaults(),
            GENERIC_WOOD_TINT,
            BroomAssets.DEFAULT,
            BroomHandling.of(HandlingProfile.SCHOOL),
            GENERIC_AUDIO,
            BroomSeat.DEFAULT);
    /** Volatile-swapped immutable map: readers never observe a mid-reload empty/partial registry. */
    private static volatile Map<Identifier, BroomDefinition> DEFINITIONS = Map.of();

    private BroomDefinitionRegistry() {
    }

    /**
     * Bumped on every swap, so anything holding a resolved definition can tell it is stale.
     *
     * <p>{@code BroomEntity} caches the record it resolved, and its id-change hook only fires when
     * the id changes. A {@code /reload} that retunes a broom in place changes no id at all, so
     * without a generation the entity would keep flying the values from before the reload for as
     * long as it lived. Compared, never dereferenced, so an int is enough.
     */
    private static volatile int generation;

    public static void replaceAll(Map<Identifier, BroomDefinition> loaded) {
        DEFINITIONS = Map.copyOf(loaded);
        generation++;
    }

    /** The current table's generation. A holder that sees a different value must re-resolve. */
    public static int generation() {
        return generation;
    }

    @Nullable
    public static BroomDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    public static Collection<BroomDefinition> getAll() {
        return List.copyOf(DEFINITIONS.values());
    }

    /** {@link #get} with the fallback folded in, for call sites that only want a tier. */
    public static BroomDefinition getOrFallback(Identifier id) {
        BroomDefinition definition = DEFINITIONS.get(id);
        return definition != null ? definition : getFallback();
    }

    /** Never null and never throws: the shipped definition if loaded, else {@link #CODE_DEFAULT}. */
    public static BroomDefinition getFallback() {
        BroomDefinition fallback = DEFINITIONS.get(FALLBACK_ID);
        return fallback != null ? fallback : CODE_DEFAULT;
    }

    /** The in-code fallback, exposed so a test can hold it against the shipped JSON. */
    public static BroomDefinition codeDefault() {
        return CODE_DEFAULT;
    }
}
