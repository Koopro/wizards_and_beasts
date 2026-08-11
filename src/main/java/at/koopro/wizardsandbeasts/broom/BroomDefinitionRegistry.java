package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
            BroomDefinition.UNTINTED);
    /** Volatile-swapped immutable map: readers never observe a mid-reload empty/partial registry. */
    private static volatile Map<Identifier, BroomDefinition> DEFINITIONS = Map.of();

    private BroomDefinitionRegistry() {
    }

    public static void replaceAll(Map<Identifier, BroomDefinition> loaded) {
        DEFINITIONS = Map.copyOf(loaded);
    }

    @Nullable
    public static BroomDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    public static Collection<BroomDefinition> getAll() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static List<BroomDefinition> getByTier(BroomTier tier) {
        List<BroomDefinition> out = new ArrayList<>();
        for (BroomDefinition definition : DEFINITIONS.values()) {
            if (definition.tier() == tier) {
                out.add(definition);
            }
        }
        out.sort(Comparator.comparing(def -> def.displayName().getString()));
        return out;
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
