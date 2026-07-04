package at.koopro.wizardsandbeasts.form;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static registry of all {@link SizeProfile} instances, keyed by profile ID.
 * <p>
 * Constructor order: (id, hitboxWidth, hitboxHeight, modelScale, modelAspectX, modelAspectZ,
 *                     reachBonus, knockbackResistance, stepHeight)
 * <p>
 * hitboxWidth/hitboxHeight = explicit collision box in blocks (1.8 = default player height).
 * modelScale = visual Y scale; modelAspectX/Z = visual width/depth ratios vs modelScale.
 */
public final class SizeProfileRegistry {

    private static final Map<String, SizeProfile> PROFILES = new LinkedHashMap<>();

    static {
        // ── Wizardkind ──
        register(new SizeProfile("wizardkind_default",
                0.60f, 1.80f,   // hitbox: 0.6 × 1.8 (standard player)
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f));

        // ── Werewolf ──
        // Human form: slightly taller, same width
        register(new SizeProfile("werewolf_human",
                0.63f, 1.89f,   // 5% taller than human
                1.05f, 0.952f, 0.952f,
                0.0f, 0.1f, 0.0f));

        // Wolf form: bulkier and taller; model wider than it is tall
        register(new SizeProfile("werewolf_wolf",
                0.78f, 2.34f,   // 1.3× human height
                1.3f, 0.923f, 0.923f,
                0.5f, 0.3f, 0.5f));

        // ── Obscurial ──
        register(new SizeProfile("obscurial_human",
                0.60f, 1.80f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f));

        // Dark cloud form: compact hitbox (~1 block tall), elongated Z for cloud silhouette
        register(new SizeProfile("obscurial_dark",
                0.34f, 1.01f,   // 0.56× human height
                0.56f, 1.0f, 1.786f,
                0.6f, 0.45f, 0.45f));

        // ── Goblin ──
        // Short and stocky: hitbox ~1.17 blocks, model wider than tall
        register(new SizeProfile("goblin_default",
                0.39f, 1.17f,   // 0.65× human height
                0.65f, 1.231f, 1.231f,
                -0.5f, 0.0f, 0.0f));

        // ── House-Elf ──
        // Smaller than goblin, similarly proportioned
        register(new SizeProfile("house_elf_default",
                0.33f, 0.99f,   // 0.55× human height
                0.55f, 1.273f, 1.273f,
                -0.5f, 0.0f, 0.0f));

        // ── Veela ──
        register(new SizeProfile("veela_human",
                0.60f, 1.80f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f));

        // Harpy form: uniform scale-up, wings handled by WING_LAYER render flag
        register(new SizeProfile("veela_harpy",
                0.66f, 1.98f,   // 1.1× human height
                1.1f, 1.0f, 1.0f,
                0.5f, 0.1f, 0.0f));

        // ── Giant ──
        // Full giant: 3.5× scale — wide but proportionally narrower than height
        register(new SizeProfile("giant_full",
                2.10f, 6.30f,   // 3.5× human height
                3.5f, 0.514f, 0.514f,
                2.0f, 0.6f, 1.5f));

        // Half-giant: 1.6× scale
        register(new SizeProfile("giant_half",
                0.96f, 2.88f,   // 1.6× human height
                1.6f, 0.813f, 0.813f,
                1.0f, 0.3f, 0.5f));

        // ── Centaur ──
        // Horse-body shape: elongated Z (front-to-back), slightly wider X
        register(new SizeProfile("centaur_default",
                0.78f, 2.34f,   // 1.3× human height
                1.3f, 1.077f, 1.385f,
                0.5f, 0.2f, 1.0f));

        // ── Vampire ──
        register(new SizeProfile("vampire_default",
                0.60f, 1.80f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.1f, 0.0f));

        // Bat form: very small hitbox, model slightly wider/deeper than tall
        register(new SizeProfile("vampire_bat",
                0.18f, 0.54f,   // 0.3× human height
                0.3f, 1.333f, 1.333f,
                -1.0f, 0.0f, 0.0f));

        // ── Merpeople ──
        register(new SizeProfile("merpeople_land",
                0.60f, 1.80f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f));

        // Water form: slightly crouched, model a touch wider
        register(new SizeProfile("merpeople_water",
                0.54f, 1.62f,   // 0.9× human height
                0.9f, 1.111f, 1.111f,
                0.5f, 0.1f, 0.0f));

        // ── Animagus beast forms ──
        // Each borrows a real vanilla entity model authored at true scale, so modelScale and
        // aspect ratios stay 1.0 (no distortion of the borrowed model). The hitbox W×H below is
        // applied verbatim by FormHitboxHandler (EntityEvent.Size) — independent of Attributes.SCALE
        // — so the collision box matches the real animal instead of a uniformly-shrunk player box.

        // Cat: vanilla CatModel — box matches an adult cat.
        register(new SizeProfile("animagus_cat",
                0.60f, 0.70f,
                1.0f, 1.0f, 1.0f,
                -0.8f, 0.0f, 0.5f));

        // Dog: vanilla WolfModel — box matches a wolf.
        register(new SizeProfile("animagus_dog",
                0.60f, 0.85f,
                1.0f, 1.0f, 1.0f,
                -0.4f, 0.1f, 0.5f));

        // Stag: no vanilla analog — placeholder geometry, tall and broad.
        register(new SizeProfile("animagus_stag",
                0.90f, 1.60f,
                1.1f, 0.95f, 1.4f,
                0.4f, 0.3f, 1.0f));

        // Hawk: vanilla ParrotModel — small perched-bird box.
        register(new SizeProfile("animagus_hawk",
                0.50f, 0.50f,
                1.0f, 1.0f, 1.0f,
                -1.0f, 0.0f, 0.0f));

        // Hare: vanilla RabbitModel — small rabbit box.
        register(new SizeProfile("animagus_hare",
                0.40f, 0.50f,
                1.0f, 1.0f, 1.0f,
                -0.9f, 0.0f, 0.5f));

        // Beetle: vanilla SilverfishModel — minuscule low box.
        register(new SizeProfile("animagus_beetle",
                0.40f, 0.30f,
                1.0f, 1.0f, 1.0f,
                -1.0f, 0.0f, 0.0f));
    }

    public static void register(SizeProfile profile) {
        PROFILES.put(profile.id(), profile);
    }

    @Nullable
    public static SizeProfile get(String id) {
        return PROFILES.get(id);
    }

    public static SizeProfile getOrDefault(String id) {
        return PROFILES.getOrDefault(id, SizeProfile.DEFAULT);
    }

    public static Map<String, SizeProfile> getAll() {
        return Collections.unmodifiableMap(PROFILES);
    }

    private SizeProfileRegistry() {}
}
