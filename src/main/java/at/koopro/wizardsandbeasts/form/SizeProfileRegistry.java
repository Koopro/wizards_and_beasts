package at.koopro.wizardsandbeasts.form;

import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static registry of all {@link SizeProfile} instances, keyed by profile ID.
 * <p>
 * Constructor order: (id, hitboxWidth, hitboxHeight, modelScale, modelAspectX, modelAspectZ,
 *                     reachBonus, knockbackResistance, stepHeight[, eyeHeight])
 * <p>
 * Omitting eyeHeight keeps vanilla's height * 0.85. Only the Animagus beasts declare one — see
 * {@link SizeProfile} for why an animal cannot use that ratio.
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
        // modelScale converts the GeckoLib rig into the hitbox, not a fraction of a 1.8-block human:
        // werewolf.geo.json is authored 2.08 blocks tall, so 2.34 / 2.08 = 1.125. The old 1.3 was the
        // human-fraction reading and drew the wolf at 2.70 blocks inside a 2.34 box — head out of it.
        register(new SizeProfile("werewolf_wolf",
                0.78f, 2.34f,
                1.125f, 0.923f, 0.923f,
                0.5f, 0.3f, 0.5f));

        // ── Obscurial ──
        register(new SizeProfile("obscurial_human",
                0.60f, 1.80f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f));

        // Dark cloud form: compact hitbox (~1 block tall), elongated Z for cloud silhouette.
        // obscurus.geo.json is 1.66 blocks tall, so 1.01 / 1.66 = 0.608; the old 0.56 was the
        // human-fraction reading and drew the cloud 8% short of its own box.
        register(new SizeProfile("obscurial_dark",
                0.34f, 1.01f,
                0.608f, 1.0f, 1.786f,
                0.6f, 0.45f, 0.45f));

        // ── Goblin ──
        // Short and stocky: hitbox ~1.17 blocks.
        //
        // modelScale is 1.17 / 1.5 because the GeckoLib rig this form draws is authored to the
        // goblin *mob's* box (ModEntities registers goblin_teller at 0.6 x 1.5), so the visual
        // multiplier has to convert 1.5 blocks of rig into 1.17 blocks of player. The old 0.65
        // was the ratio against a 1.8-block human placeholder and drew the rig at 0.98 blocks
        // inside a 1.17 box. The 1.231 aspects came from the same placeholder — a proportional
        // humanoid needed widening to look goblin-ish; this rig is already stocky, and stretching
        // it a further 23% makes it barrel-shaped.
        register(new SizeProfile("goblin_default",
                0.39f, 1.17f,
                0.78f, 1.0f, 1.0f,
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
        // centaur.geo.json is 2.25 blocks tall since the wave-1 rig (tools/centaur_model.py), so
        // 2.34 / 2.25 = 1.040. The previous 1.570 was fitted to the 1.49-block box rig it replaced.
        // Aspect is 1.0 on both axes: the 1.077 / 1.385 stretch gave a box rig a horse's length and
        // girth, and applied to a rig that already has them it drew a 2.4-block dachshund.
        register(new SizeProfile("centaur_default",
                0.78f, 2.34f,
                1.040f, 1.0f, 1.0f,
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

        // Water form: drawn by merperson.geo.json since that rig was built (tools/merperson_model.py),
        // which is 1.625 blocks tall, so 1.62 / 1.625 = 0.997. The 0.9 and the 1.111 aspect were the
        // human-fraction reading of the placeholder MerfolkSwimModel; a real rig wants 1.0 on both axes.
        register(new SizeProfile("merpeople_water",
                0.54f, 1.62f,
                0.997f, 1.0f, 1.0f,
                0.5f, 0.1f, 0.0f));

        // ── Animagus beast forms ──
        // Each borrows a real vanilla entity model authored at true scale, so modelScale and
        // aspect ratios stay 1.0 (no distortion of the borrowed model). The hitbox W×H below is
        // applied verbatim by PlayerBoxOverrides (LivingEntityDimensionsMixin) — independent of Attributes.SCALE
        // — so the collision box matches the real animal instead of a uniformly-shrunk player box.

        // Every beast form declares its eye height rather than inheriting vanilla's height * 0.85.
        // That ratio describes something standing upright: on a cat it puts the camera above the
        // ears, on a beetle it puts it above the shell. Where a datapack definition exists the two
        // must agree, which AnimagusEyeHeightParityTest enforces.

        // Cat: vanilla CatModel — box matches an adult cat.
        // Eye 0.55 of 0.70: a cat carries its head high, but not at the top of its back.
        register(new SizeProfile("animagus_cat",
                0.60f, 0.70f,
                1.0f, 1.0f, 1.0f,
                -0.8f, 0.0f, 0.5f,
                0.55f));

        // Dog: vanilla WolfModel — box matches a wolf.
        // Eye 0.75 of 0.85: muzzle-forward head, level with the shoulders.
        register(new SizeProfile("animagus_dog",
                0.60f, 0.85f,
                1.0f, 1.0f, 1.0f,
                -0.4f, 0.1f, 0.5f,
                0.75f));

        // Stag: no vanilla analog — placeholder geometry, tall and broad.
        // Eye 1.45 of 1.60: the head is raised well above the back, which the flat ratio's 1.36
        // would have sunk into the shoulders.
        register(new SizeProfile("animagus_stag",
                0.90f, 1.60f,
                1.1f, 0.95f, 1.4f,
                0.4f, 0.3f, 1.0f,
                1.45f));

        // Hawk: vanilla ParrotModel — small perched-bird box.
        // Eye 0.42 of 0.50: a perched bird's eyes really are near the top. Declared anyway, so a
        // later change to the box height cannot quietly move the camera with it.
        register(new SizeProfile("animagus_hawk",
                0.50f, 0.50f,
                1.0f, 1.0f, 1.0f,
                -1.0f, 0.0f, 0.0f,
                0.42f));

        // Hare: vanilla RabbitModel — small rabbit box.
        // Eye 0.40 of 0.50: below the ears, which is most of a hare's height.
        register(new SizeProfile("animagus_hare",
                0.40f, 0.50f,
                1.0f, 1.0f, 1.0f,
                -0.9f, 0.0f, 0.5f,
                0.40f));

        // Beetle: vanilla SilverfishModel — minuscule low box.
        // Eye 0.20 of 0.30: at the front of the shell, not on top of it.
        register(new SizeProfile("animagus_beetle",
                0.40f, 0.30f,
                1.0f, 1.0f, 1.0f,
                -1.0f, 0.0f, 0.0f,
                0.20f));
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
