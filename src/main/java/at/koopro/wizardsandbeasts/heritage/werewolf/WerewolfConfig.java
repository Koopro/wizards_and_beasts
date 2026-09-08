package at.koopro.wizardsandbeasts.heritage.werewolf;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

/**
 * Every tunable of the lycanthropy layer, appended to the mod's single {@code ModConfigSpec}.
 *
 * <p>Keys are declared <b>flat</b> with a {@code werewolf} prefix rather than inside a
 * {@code push("werewolf")} section, and that is deliberate: {@code WizardsConfigScreen} walks only the
 * top level of {@code SPEC.getValues().valueMap()}, so anything nested in a section is invisible in
 * the in-game config screen. The Floo and broom blocks in {@link at.koopro.wizardsandbeasts.Config}
 * are flat for the same reason.
 *
 * <p>The cached static fields are seeded with their shipped defaults, not left at zero. A moon scan
 * can run before {@code ModConfigEvent} has fired on a client joining a world, and a zero
 * {@link #transformDelayTicks} would complete the change on the tick it started — the same trap the
 * Floo timings comment in {@code Config} describes.
 *
 * <p>Defaults follow the books: the change is <em>forced</em> under a full moon, and Wolfsbane
 * restores the mind without touching the shape. {@link #wolfsbaneSuppressesTransform} exists for
 * servers that would rather the potion be a cure, and ships off.
 */
@NullMarked
public final class WerewolfConfig {

    private WerewolfConfig() {}

    // ── spec holders ───────────────────────────────────────────────────

    private static ModConfigSpec.BooleanValue ENABLE_FORCED_TRANSFORM;
    private static ModConfigSpec.IntValue EXPOSURE_THRESHOLD;
    private static ModConfigSpec.IntValue EXPOSURE_GAIN;
    private static ModConfigSpec.IntValue EXPOSURE_DECAY;
    private static ModConfigSpec.IntValue TRANSFORM_DELAY_TICKS;
    private static ModConfigSpec.BooleanValue ENABLE_LOSS_OF_CONTROL;
    private static ModConfigSpec.DoubleValue AGGRO_RADIUS;
    private static ModConfigSpec.DoubleValue CHARGE_SPEED;
    private static ModConfigSpec.IntValue BITE_COOLDOWN_TICKS;
    private static ModConfigSpec.DoubleValue DRIFT_CORRECTION_BLOCKS;
    private static ModConfigSpec.BooleanValue PACK_BETRAYAL;
    private static ModConfigSpec.BooleanValue DROP_UNSAFE_EQUIPMENT;
    private static ModConfigSpec.ConfigValue<List<? extends String>> EQUIPMENT_WHITELIST;
    private static ModConfigSpec.BooleanValue WOLFSBANE_SUPPRESSES_TRANSFORM;
    private static ModConfigSpec.IntValue WOLFSBANE_DURATION_TICKS;
    private static ModConfigSpec.BooleanValue WOLFSBANE_AMPLIFIER_EXTENDS;
    private static ModConfigSpec.IntValue POST_TRANSFORM_DEBUFF_TICKS;
    private static ModConfigSpec.DoubleValue HEALTH_BONUS;
    private static ModConfigSpec.DoubleValue SPEED_BONUS;
    private static ModConfigSpec.DoubleValue ARMOR_BONUS;
    private static ModConfigSpec.DoubleValue ATTACK_DAMAGE_BONUS;
    private static ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE_BONUS;

    // ── cached values, seeded with the shipped defaults ────────────────

    public static boolean enableForcedTransform = true;
    public static int exposureThreshold = 60;
    public static int exposureGain = 4;
    public static int exposureDecay = 6;
    public static int transformDelayTicks = 50;
    public static boolean enableLossOfControl = true;
    public static double aggroRadius = 24.0;
    public static double chargeSpeed = 0.29;
    public static int biteCooldownTicks = 12;
    public static double driftCorrectionBlocks = 3.0;
    public static boolean packBetrayal = false;
    public static boolean dropUnsafeEquipment = false;
    private static List<? extends String> equipmentWhitelist = List.of();
    public static boolean wolfsbaneSuppressesTransform = false;
    public static int wolfsbaneDurationTicks = 3600;
    public static boolean wolfsbaneAmplifierExtendsDuration = true;
    public static int postTransformDebuffTicks = 200;
    public static double healthBonus = 6.0;
    public static double speedBonus = 0.035;
    public static double armorBonus = 4.0;
    public static double attackDamageBonus = 4.0;
    public static double knockbackResistanceBonus = 0.3;

    /** Declares every werewolf key on the mod's builder. Called while the spec is being assembled. */
    public static void define(ModConfigSpec.Builder builder) {
        ENABLE_FORCED_TRANSFORM = builder
                .comment("If true, a werewolf under a full moon is taken by it whether they want to be or not.",
                        "Turning this off leaves the werewolf heritage intact — its stats, its forms and the",
                        "manual form commands all still work; only the compulsion goes away.")
                .define("werewolfEnableForcedTransform", true);
        EXPOSURE_THRESHOLD = builder
                .comment("Moonlight the wolf must soak up before the change begins, in exposure points.",
                        "The counter rises by werewolfExposureGain on every scan under open sky and falls by",
                        "werewolfExposureDecay under a roof, so a cellar is a real defence and a slow walk home",
                        "is not. Set 0 to make the change instant on moonrise.")
                .defineInRange("werewolfExposureThreshold", 60, 0, 20000);
        EXPOSURE_GAIN = builder
                .comment("Exposure points gained per scan (one second) while the moon can see the player.")
                .defineInRange("werewolfExposureGain", 4, 1, 1000);
        EXPOSURE_DECAY = builder
                .comment("Exposure points lost per scan while roofed or underground. Larger than the gain on",
                        "purpose: a werewolf who runs for cover should lose ground faster than they made it.")
                .defineInRange("werewolfExposureDecay", 6, 0, 1000);
        TRANSFORM_DELAY_TICKS = builder
                .comment("Ticks the transformation spends in TRANSITIONING before the wolf form lands.",
                        "This is the window the particles and the scream occupy, and it is the last moment a",
                        "player can be reached by anything that cancels the change.")
                .defineInRange("werewolfTransformDelayTicks", 50, 1, 600);
        ENABLE_LOSS_OF_CONTROL = builder
                .comment("If true, an unmedicated wolf is driven by the mod, not by its player: movement,",
                        "facing and targets are all taken over. See documentation/WEREWOLF_LOSS_OF_CONTROL.md.",
                        "Off leaves the player in a wolf's body with a wolf's stats and their own mind — the",
                        "Wolfsbane outcome, for everyone, permanently.")
                .define("werewolfLossOfControl", true);
        AGGRO_RADIUS = builder
                .comment("How far a feral wolf looks for something to kill, in blocks.")
                .defineInRange("werewolfAggroRadius", 24.0, 4.0, 96.0);
        CHARGE_SPEED = builder
                .comment("Horizontal speed the controller drives a feral wolf at, in blocks per tick.",
                        "0.29 is a little over a sprint; the wolf should be frightening to be chased by.")
                .defineInRange("werewolfChargeSpeed", 0.29, 0.05, 1.0);
        BITE_COOLDOWN_TICKS = builder
                .comment("Ticks between bites once the wolf is in reach.")
                .defineInRange("werewolfBiteCooldownTicks", 12, 1, 200);
        DRIFT_CORRECTION_BLOCKS = builder
                .comment("How far a driven wolf may end up from where the controller put it before the server",
                        "snaps it back. This is what makes loss of control server-authoritative rather than a",
                        "polite request to the client: a client that ignores the input theft still cannot walk",
                        "away. Raise it if legitimate movement (boats, elytra, high ping) is being corrected.")
                .defineInRange("werewolfDriftCorrectionBlocks", 3.0, 0.5, 32.0);
        PACK_BETRAYAL = builder
                .comment("If true, a feral wolf will attack other werewolves. Off by default: the pack is the",
                        "one thing the wolf recognises, and a server night that turns two players on each other",
                        "the moment they meet is not the story the books tell.")
                .define("werewolfPackBetrayal", false);
        DROP_UNSAFE_EQUIPMENT = builder
                .comment("What happens to gear a wolf cannot wear. false (default) unequips it into the",
                        "player's own inventory, which is safe and reversible; true throws it on the ground",
                        "where the change happened, which is closer to the books and much crueller.")
                .define("werewolfDropUnsafeEquipment", false);
        EQUIPMENT_WHITELIST = builder
                .comment("Item ids a werewolf may keep equipped through the change, e.g.",
                        "\"wizards_and_beasts:demiguise_cloak\". Everything else in an armour or hand slot is",
                        "unequipped per werewolfDropUnsafeEquipment. Ids are matched exactly, namespace included;",
                        "a bare id is read in the minecraft namespace.")
                // defineListAllowEmpty, not defineList: defineList pins the spec to ListValueSpec.NON_EMPTY,
                // so an empty default is judged "the wrong size" on every single load. The correction
                // rewrote the file, the config FileWatcher saw the write and reloaded, and the reload
                // failed the same check — a rewrite loop that ran for as long as the server was up,
                // once a second, spamming WARN and rolling a new .toml.bak each time. The key never
                // survived a write either, so no whitelist could be configured at all. Empty is the
                // correct default here: by default a wolf keeps nothing on.
                .defineListAllowEmpty("werewolfEquipmentWhitelist", List.<String>of(), () -> "",
                        entry -> entry instanceof String);
        WOLFSBANE_SUPPRESSES_TRANSFORM = builder
                .comment("If true, Wolfsbane stops the change happening at all. OFF by default and deliberately:",
                        "canon is explicit that Lupin still becomes a wolf — the potion preserves his mind, not",
                        "his shape. With this off, Wolfsbane means the wolf keeps its reason and its player.")
                .define("werewolfWolfsbaneSuppressesTransform", false);
        WOLFSBANE_DURATION_TICKS = builder
                .comment("Base duration of the Wolfsbane effect, in ticks. 3600 = three minutes, about a third",
                        "of a Minecraft night, so a single dose is not a whole moon unless it is brewed well.",
                        "Governs doses applied by code (/wandb player heritage werewolf wolfsbane, and addons).",
                        "The BREWED potion carries its own duration in its datapack file,",
                        "data/wizards_and_beasts/brews/wolfsbane_potion.json — a brew's strength belongs to the",
                        "datapack in this mod, and editing that file is how you change what a bottle is worth.")
                .defineInRange("werewolfWolfsbaneDurationTicks", 3600, 200, 288000);
        WOLFSBANE_AMPLIFIER_EXTENDS = builder
                .comment("If true, a stronger brew lasts longer (duration doubles per amplifier level) rather",
                        "than being more potent — there is nothing for potency to do, since reason is not a",
                        "quantity. Off makes every dose the base duration regardless of amplifier.")
                .define("werewolfWolfsbaneAmplifierExtendsDuration", true);
        POST_TRANSFORM_DEBUFF_TICKS = builder
                .comment("How long the exhaustion after a change lasts: Weakness, Slowness and Hunger.",
                        "Set 0 to walk away from a night as a wolf with nothing to show for it.")
                .defineInRange("werewolfPostTransformDebuffTicks", 200, 0, 24000);
        HEALTH_BONUS = builder
                .comment("Extra max health in the wolf form, in half-hearts. Applied as an attribute modifier",
                        "with its own id, so it composes with the heritage's own stats instead of replacing them.",
                        "These five values are the whole werewolf_wolf combat profile; they are hard-coded",
                        "defaults awaiting a datapack form-stat layer (see the package javadoc).")
                .defineInRange("werewolfHealthBonus", 6.0, 0.0, 100.0);
        SPEED_BONUS = builder
                .comment("Extra movement speed in the wolf form (base player speed is 0.1).")
                .defineInRange("werewolfSpeedBonus", 0.035, 0.0, 1.0);
        ARMOR_BONUS = builder
                .comment("Armour points from hide and bulk in the wolf form.")
                .defineInRange("werewolfArmorBonus", 4.0, 0.0, 30.0);
        ATTACK_DAMAGE_BONUS = builder
                .comment("Extra attack damage in the wolf form. A bare-handed player deals 1, so 4 makes an",
                        "unarmed bite roughly an iron sword.")
                .defineInRange("werewolfAttackDamageBonus", 4.0, 0.0, 50.0);
        KNOCKBACK_RESISTANCE_BONUS = builder
                .comment("Knockback resistance in the wolf form, 0..1.")
                .defineInRange("werewolfKnockbackResistanceBonus", 0.3, 0.0, 1.0);
    }

    /** Pulls every value into the cached fields. Called from the mod's {@code ModConfigEvent} handler. */
    public static void refresh() {
        enableForcedTransform = ENABLE_FORCED_TRANSFORM.get();
        exposureThreshold = EXPOSURE_THRESHOLD.get();
        exposureGain = EXPOSURE_GAIN.get();
        exposureDecay = EXPOSURE_DECAY.get();
        transformDelayTicks = TRANSFORM_DELAY_TICKS.get();
        enableLossOfControl = ENABLE_LOSS_OF_CONTROL.get();
        aggroRadius = AGGRO_RADIUS.get();
        chargeSpeed = CHARGE_SPEED.get();
        biteCooldownTicks = BITE_COOLDOWN_TICKS.get();
        driftCorrectionBlocks = DRIFT_CORRECTION_BLOCKS.get();
        packBetrayal = PACK_BETRAYAL.get();
        dropUnsafeEquipment = DROP_UNSAFE_EQUIPMENT.get();
        equipmentWhitelist = EQUIPMENT_WHITELIST.get();
        wolfsbaneSuppressesTransform = WOLFSBANE_SUPPRESSES_TRANSFORM.get();
        wolfsbaneDurationTicks = WOLFSBANE_DURATION_TICKS.get();
        wolfsbaneAmplifierExtendsDuration = WOLFSBANE_AMPLIFIER_EXTENDS.get();
        postTransformDebuffTicks = POST_TRANSFORM_DEBUFF_TICKS.get();
        healthBonus = HEALTH_BONUS.get();
        speedBonus = SPEED_BONUS.get();
        armorBonus = ARMOR_BONUS.get();
        attackDamageBonus = ATTACK_DAMAGE_BONUS.get();
        knockbackResistanceBonus = KNOCKBACK_RESISTANCE_BONUS.get();
    }

    /**
     * True when {@code itemId} may stay equipped through the change.
     *
     * <p>Bare ids in the config are read as {@code minecraft:} entries, matching how a player would
     * type one. Never throws on a malformed entry — a typo in a whitelist must not stop a
     * transformation, it just fails to protect the item somebody meant to name.
     */
    public static boolean isEquipmentWhitelisted(String itemId) {
        if (equipmentWhitelist.isEmpty()) {
            return false;
        }
        String needle = itemId.toLowerCase(Locale.ROOT);
        for (String entry : equipmentWhitelist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String candidate = entry.trim().toLowerCase(Locale.ROOT);
            if (!candidate.contains(":")) {
                candidate = "minecraft:" + candidate;
            }
            if (candidate.equals(needle)) {
                return true;
            }
        }
        return false;
    }
}
