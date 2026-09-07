package at.koopro.wizardsandbeasts.heritage.vampire;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NullMarked;

/**
 * Every tunable of the blood economy, appended to the mod's single {@code ModConfigSpec}.
 *
 * <p>Keys are declared <b>flat</b> with a {@code vampireBlood} prefix rather than inside a
 * {@code push("vampireBlood")} section, for the reason {@code WerewolfConfig}'s header spells out:
 * {@code WizardsConfigScreen} walks only the top level of {@code SPEC.getValues().valueMap()}, so a
 * nested key is invisible in the in-game config screen.
 *
 * <p>The cached fields carry their shipped defaults rather than starting at zero. The blood tick can run
 * before {@code ModConfigEvent} has fired — a single-player world is ticking while the config is still
 * loading — and a zero {@link #maxBlood} would divide every percentage by nothing.
 *
 * <p>Nothing here is a list, which is deliberate: see {@code WerewolfConfig}'s
 * {@code defineListAllowEmpty} comment for the config-rewrite loop an empty {@code defineList} default
 * causes. The one collection this system needs is a datapack tag ({@code bloodless}), not a config key.
 */
@NullMarked
public final class VampireBloodConfig {

    private VampireBloodConfig() {}

    // -- spec holders ---------------------------------------------------

    private static ModConfigSpec.DoubleValue MAX_BLOOD;
    private static ModConfigSpec.DoubleValue DRAIN_PER_SECOND;
    private static ModConfigSpec.IntValue RESPAWN_PERCENT;
    private static ModConfigSpec.IntValue SATED_FLOOR_PERCENT;
    private static ModConfigSpec.IntValue THIRSTY_FLOOR_PERCENT;
    private static ModConfigSpec.IntValue PARCHED_FLOOR_PERCENT;
    private static ModConfigSpec.DoubleValue STARVATION_DAMAGE_FLOOR;
    private static ModConfigSpec.DoubleValue STARVATION_DAMAGE;
    private static ModConfigSpec.IntValue FEED_COOLDOWN_TICKS;
    private static ModConfigSpec.IntValue DRAINED_IMMUNITY_TICKS;
    private static ModConfigSpec.DoubleValue FEED_PER_MAX_HEALTH;
    private static ModConfigSpec.DoubleValue FEED_MIN;
    private static ModConfigSpec.DoubleValue FEED_MAX;
    private static ModConfigSpec.DoubleValue HOSTILE_FEED_MULTIPLIER;
    private static ModConfigSpec.DoubleValue FEED_DAMAGE;
    private static ModConfigSpec.BooleanValue ALLOW_FEEDING_ON_PLAYERS;
    private static ModConfigSpec.IntValue BLOOD_RUSH_TICKS;

    // -- cached values, seeded with the shipped defaults -----------------

    public static float maxBlood = 100.0f;
    public static float drainPerSecond = 0.2f;
    public static float respawnPercent = 0.40f;
    public static float satedFloor = 0.70f;
    public static float thirstyFloor = 0.40f;
    public static float parchedFloor = 0.15f;
    public static float starvationDamageFloor = 5.0f;
    public static float starvationDamage = 1.0f;
    public static int feedCooldownTicks = 40;
    public static int drainedImmunityTicks = 200;
    public static float feedPerMaxHealth = 1.5f;
    public static float feedMin = 6.0f;
    public static float feedMax = 25.0f;
    public static float hostileFeedMultiplier = 1.25f;
    public static float feedDamage = 2.0f;
    public static boolean allowFeedingOnPlayers = false;
    public static int bloodRushTicks = 100;

    /** Declares every blood key on the mod's builder. Called while the spec is being assembled. */
    public static void define(ModConfigSpec.Builder builder) {
        MAX_BLOOD = builder
                .comment("Size of a blood-drinker's pool. Every other number in this block that is expressed",
                        "in blood points is relative to this one, so halving it halves the value of a feed as",
                        "well as the cost of a night. Stored per player when a heritage is committed, so",
                        "raising it reaches existing vampires only on their next full refill.")
                .defineInRange("vampireBloodMaxBlood", 100.0, 1.0, 10000.0);
        DRAIN_PER_SECOND = builder
                .comment("Blood lost per second while simply being awake. At the defaults a full pool empties",
                        "in a little over eight minutes of play, so feeding is a rhythm rather than an errand.",
                        "Set 0 to make blood a resource that only abilities and injuries spend.")
                .defineInRange("vampireBloodDrainPerSecond", 0.2, 0.0, 100.0);
        RESPAWN_PERCENT = builder
                .comment("Percent of the pool a vampire wakes up with after dying. 40 is THIRSTY: enough to",
                        "leave the graveyard, not enough to have profited from the trip. 100 would make dying",
                        "the cheapest meal in the game.")
                .defineInRange("vampireBloodRespawnPercent", 40, 0, 100);
        SATED_FLOOR_PERCENT = builder
                .comment("Percent of the pool at or above which a vampire is SATED, with no penalties at all.")
                .defineInRange("vampireBloodSatedFloorPercent", 70, 0, 100);
        THIRSTY_FLOOR_PERCENT = builder
                .comment("Percent at or above which a vampire is THIRSTY: told about it, not yet punished.")
                .defineInRange("vampireBloodThirstyFloorPercent", 40, 0, 100);
        PARCHED_FLOOR_PERCENT = builder
                .comment("Percent at or above which a vampire is PARCHED. Below this they are STARVING.",
                        "These three should descend; if they do not, ThirstStage.of takes the first band that",
                        "accepts the value rather than leaving a band unreachable.")
                .defineInRange("vampireBloodParchedFloorPercent", 15, 0, 100);
        STARVATION_DAMAGE_FLOOR = builder
                .comment("Blood points at or below which starvation starts doing damage. Above it, STARVING is",
                        "debuffs only, so there is a last stretch of empty in which a vampire can still save",
                        "themselves by hunting.")
                .defineInRange("vampireBloodStarvationDamageFloor", 5.0, 0.0, 10000.0);
        STARVATION_DAMAGE = builder
                .comment("Damage per starvation tick (once every two seconds) once the floor is crossed.",
                        "Set 0 for a server where running dry is only ever crippling and never lethal.")
                .defineInRange("vampireBloodStarvationDamage", 1.0, 0.0, 100.0);
        FEED_COOLDOWN_TICKS = builder
                .comment("Ticks between one vampire's feeds, on any target. 40 = two seconds.")
                .defineInRange("vampireBloodFeedCooldownTicks", 40, 0, 24000);
        DRAINED_IMMUNITY_TICKS = builder
                .comment("Ticks a drained creature stays drained for. It yields nothing during this window,",
                        "which is what stops one penned cow being an infinite blood supply. 200 = ten seconds.")
                .defineInRange("vampireBloodDrainedImmunityTicks", 200, 0, 24000);
        FEED_PER_MAX_HEALTH = builder
                .comment("Blood gained per point of the target's maximum health. A cow (10) yields 15 at the",
                        "default, before the clamps and the hostile multiplier below.")
                .defineInRange("vampireBloodFeedPerMaxHealth", 1.5, 0.0, 100.0);
        FEED_MIN = builder
                .comment("Least blood any single feed may yield, however small the creature. Keeps a chicken",
                        "worth biting.")
                .defineInRange("vampireBloodFeedMin", 6.0, 0.0, 10000.0);
        FEED_MAX = builder
                .comment("Most blood any single feed may yield. This is the load-bearing balance number: no",
                        "single bite should refill the pool, or the thirst stages never happen.")
                .defineInRange("vampireBloodFeedMax", 25.0, 0.0, 10000.0);
        HOSTILE_FEED_MULTIPLIER = builder
                .comment("Multiplier on blood taken from something that fights back. Applied before the clamps.")
                .defineInRange("vampireBloodHostileFeedMultiplier", 1.25, 0.0, 100.0);
        FEED_DAMAGE = builder
                .comment("Damage a feed does to the creature fed on, in half-hearts. 2.0 = one heart: enough",
                        "that a herd notices, little enough that feeding is not just a slow way to kill.")
                .defineInRange("vampireBloodFeedDamage", 2.0, 0.0, 100.0);
        ALLOW_FEEDING_ON_PLAYERS = builder
                .comment("If true, a vampire may feed on other players. OFF by default: it is a PvP mechanic",
                        "with no consent step yet, and a server that has not opted into PvP should not acquire",
                        "one through a heritage. Player feeds still obey every other rule, including the",
                        "drained window and the feed cooldown.")
                .define("vampireBloodAllowFeedingOnPlayers", false);
        BLOOD_RUSH_TICKS = builder
                .comment("Duration of the brief Speed/Strength rush after a successful feed. Set 0 to remove",
                        "the reward and leave feeding purely a refuel.")
                .defineInRange("vampireBloodRushTicks", 100, 0, 24000);
    }

    /** Pulls every value into the cached fields. Called from the mod's {@code ModConfigEvent} handler. */
    public static void refresh() {
        maxBlood = MAX_BLOOD.get().floatValue();
        drainPerSecond = DRAIN_PER_SECOND.get().floatValue();
        respawnPercent = RESPAWN_PERCENT.get() / 100.0f;
        satedFloor = SATED_FLOOR_PERCENT.get() / 100.0f;
        thirstyFloor = THIRSTY_FLOOR_PERCENT.get() / 100.0f;
        parchedFloor = PARCHED_FLOOR_PERCENT.get() / 100.0f;
        starvationDamageFloor = STARVATION_DAMAGE_FLOOR.get().floatValue();
        starvationDamage = STARVATION_DAMAGE.get().floatValue();
        feedCooldownTicks = FEED_COOLDOWN_TICKS.get();
        drainedImmunityTicks = DRAINED_IMMUNITY_TICKS.get();
        feedPerMaxHealth = FEED_PER_MAX_HEALTH.get().floatValue();
        feedMin = FEED_MIN.get().floatValue();
        feedMax = FEED_MAX.get().floatValue();
        hostileFeedMultiplier = HOSTILE_FEED_MULTIPLIER.get().floatValue();
        feedDamage = FEED_DAMAGE.get().floatValue();
        allowFeedingOnPlayers = ALLOW_FEEDING_ON_PLAYERS.get();
        bloodRushTicks = BLOOD_RUSH_TICKS.get();
    }
}
