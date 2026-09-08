package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.SoulDrainedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<MobEffect, StupefyEffect> STUPEFY =
            MOB_EFFECTS.register("stupefy", StupefyEffect::new);
    public static final DeferredHolder<MobEffect, PetrificusTotalusEffect> PETRIFICUS_TOTALUS =
            MOB_EFFECTS.register("petrificus_totalus", PetrificusTotalusEffect::new);
    public static final DeferredHolder<MobEffect, MandrakeRestorationEffect> MANDRAKE_RESTORATION =
            MOB_EFFECTS.register("mandrake_restoration", MandrakeRestorationEffect::new);
    public static final DeferredHolder<MobEffect, ImpedimentaEffect> IMPEDIMENTA =
            MOB_EFFECTS.register("impedimenta", ImpedimentaEffect::new);
    public static final DeferredHolder<MobEffect, TarantallegraEffect> TARANTALLEGRA =
            MOB_EFFECTS.register("tarantallegra", TarantallegraEffect::new);
    public static final DeferredHolder<MobEffect, ConfundoEffect> CONFUNDO =
            MOB_EFFECTS.register("confundo", ConfundoEffect::new);
    public static final DeferredHolder<MobEffect, LanglockEffect> LANGLOCK =
            MOB_EFFECTS.register("langlock", LanglockEffect::new);
    public static final DeferredHolder<MobEffect, EngorgioEffect> ENGORGIO =
            MOB_EFFECTS.register("engorgio", EngorgioEffect::new);
    public static final DeferredHolder<MobEffect, ReducioEffect> REDUCIO =
            MOB_EFFECTS.register("reducio", ReducioEffect::new);
    public static final DeferredHolder<MobEffect, ObscuroEffect> OBSCURO =
            MOB_EFFECTS.register("obscuro", ObscuroEffect::new);
    public static final DeferredHolder<MobEffect, LumosFieldEffect> LUMOS_FIELD =
            MOB_EFFECTS.register("lumos_field", LumosFieldEffect::new);
    /** A Demiguise's trick: mobs look past you until you give them a reason not to. */
    public static final DeferredHolder<MobEffect, CamouflageEffect> CAMOUFLAGE =
            MOB_EFFECTS.register("camouflage", CamouflageEffect::new);
    /** Half-there and quick, at the price of a light tax. See {@code ShadowForm}. */
    public static final DeferredHolder<MobEffect, ShadowFormEffect> SHADOW_FORM =
            MOB_EFFECTS.register("shadow_form", ShadowFormEffect::new);
    /** Butterbeer warmth: no more freezing, and golden steam that says so. */
    public static final DeferredHolder<MobEffect, WarmthEffect> WARMTH =
            MOB_EFFECTS.register("warmth", WarmthEffect::new);
    /** Butterbeer calm: neutral things stop minding you until you hit one. */
    public static final DeferredHolder<MobEffect, MellowEffect> MELLOW =
            MOB_EFFECTS.register("mellow", MellowEffect::new);
    /** A clear head: +10% from what you kill and what you study. See {@code HogwartsComfort}. */
    public static final DeferredHolder<MobEffect, HogwartsComfortEffect> HOGWARTS_COMFORT =
            MOB_EFFECTS.register("hogwarts_comfort", HogwartsComfortEffect::new);
    /** Half a minute after chocolate when the despair cannot come back. */
    public static final DeferredHolder<MobEffect, ChocolateWardEffect> CHOCOLATE_WARD =
            MOB_EFFECTS.register("chocolate_ward", ChocolateWardEffect::new);
    /** Riding a bubble of Droobles. See {@code BubbleFloatHandler}. */
    public static final DeferredHolder<MobEffect, BubbleFloatEffect> BUBBLE_FLOAT =
            MOB_EFFECTS.register("bubble_float", BubbleFloatEffect::new);
    /** The three seconds a mouthful of Firewhisky is still going down. */
    public static final DeferredHolder<MobEffect, FirewhiskyBurnEffect> FIREWHISKY_BURN =
            MOB_EFFECTS.register("firewhisky_burn", FirewhiskyBurnEffect::new);
    /** Three in two minutes. Blocks casting; see {@code SpellCastGate.TOO_DRUNK}. */
    public static final DeferredHolder<MobEffect, DrunkEffect> DRUNK =
            MOB_EFFECTS.register("drunk", DrunkEffect::new);
    /** Gillyweed: gills, webbed hands, and a lake that stops being a place you visit. */
    public static final DeferredHolder<MobEffect, GillsEffect> GILLS =
            MOB_EFFECTS.register("gills", GillsEffect::new);
    /** Luna's trick: anything nearby that is hiding gets outlined, for you alone. */
    public static final DeferredHolder<MobEffect, WrackspurtSightEffect> WRACKSPURT_SIGHT =
            MOB_EFFECTS.register("wrackspurt_sight", WrackspurtSightEffect::new);
    /** A slice of treacle tart: a slow heart, and fear passing sooner. */
    public static final DeferredHolder<MobEffect, HomeComfortEffect> HOME_COMFORT =
            MOB_EFFECTS.register("home_comfort", HomeComfortEffect::new);
    /** Fizzing Whizzbee: the whizz on each jump, the sputter, and the pop. */
    public static final DeferredHolder<MobEffect, FizzingEffect> FIZZING =
            MOB_EFFECTS.register("fizzing", FizzingEffect::new);
    /** The toad, still hopping, from the inside. */
    public static final DeferredHolder<MobEffect, PeppermintHopEffect> PEPPERMINT_HOP =
            MOB_EFFECTS.register("peppermint_hop", PeppermintHopEffect::new);
    public static final DeferredHolder<MobEffect, MuffliatoEffect> MUFFLIATO =
            MOB_EFFECTS.register("muffliato", MuffliatoEffect::new);
    public static final DeferredHolder<MobEffect, CruciatusPainEffect> CRUCIATUS_PAIN =
            MOB_EFFECTS.register("cruciatus_pain", CruciatusPainEffect::new);
    public static final DeferredHolder<MobEffect, DementorChillEffect> DEMENTOR_CHILL =
            MOB_EFFECTS.register("dementor_chill", DementorChillEffect::new);
    public static final DeferredHolder<MobEffect, SplinchedEffect> SPLINCHED =
            MOB_EFFECTS.register("splinched", SplinchedEffect::new);
    public static final DeferredHolder<MobEffect, SectumsempraBleedEffect> SECTUMSEMPRA_BLEED =
            MOB_EFFECTS.register("sectumsempra_bleed", SectumsempraBleedEffect::new);
    public static final DeferredHolder<MobEffect, FurnunculusEffect> FURNUNCULUS =
            MOB_EFFECTS.register("furnunculus", FurnunculusEffect::new);
    public static final DeferredHolder<MobEffect, JellyLegsEffect> JELLY_LEGS =
            MOB_EFFECTS.register("jelly_legs", JellyLegsEffect::new);
    public static final DeferredHolder<MobEffect, BatBogeyEffect> BAT_BOGEY =
            MOB_EFFECTS.register("bat_bogey", BatBogeyEffect::new);

    public static final DeferredHolder<MobEffect, ExpelliarmusDisarmedEffect> EXPELLIARMUS_DISARMED =
            MOB_EFFECTS.register("expelliarmus_disarmed", ExpelliarmusDisarmedEffect::new);
    public static final DeferredHolder<MobEffect, ProtegoShieldEffect> PROTEGO_SHIELD =
            MOB_EFFECTS.register("protego_shield", ProtegoShieldEffect::new);
    public static final DeferredHolder<MobEffect, ImperioEuphoriaEffect> IMPERIO_EUPHORIA =
            MOB_EFFECTS.register("imperio_euphoria", ImperioEuphoriaEffect::new);
    public static final DeferredHolder<MobEffect, ImperioResistingEffect> IMPERIO_RESISTING =
            MOB_EFFECTS.register("imperio_resisting", ImperioResistingEffect::new);
    public static final DeferredHolder<MobEffect, CrucioSanityDrainEffect> CRUCIO_SANITY_DRAIN =
            MOB_EFFECTS.register("crucio_sanity_drain", CrucioSanityDrainEffect::new);

    /** Standing in a lit Floo hearth. See {@code FlooProtectionEvents}. */
    public static final DeferredHolder<MobEffect, FlooProtectedEffect> FLOO_PROTECTED =
            MOB_EFFECTS.register("floo_protected", FlooProtectedEffect::new);

    public static final DeferredHolder<MobEffect, DisorientedEffect> DISORIENTED =
            MOB_EFFECTS.register("disoriented", DisorientedEffect::new);

    public static final DeferredHolder<MobEffect, SunderedEffect> SUNDERED =
            MOB_EFFECTS.register("sundered", SunderedEffect::new);

    public static final DeferredHolder<MobEffect, SoulDrainedEffect> SOUL_DRAINED =
            MOB_EFFECTS.register("soul_drained", SoulDrainedEffect::new);

    public static final DeferredHolder<MobEffect, BasiliskGazeLockEffect> BASILISK_GAZE_LOCK =
            MOB_EFFECTS.register("basilisk_gaze_lock", BasiliskGazeLockEffect::new);

    /**
     * A transformed body's nose. Marker only — granted by {@code FormSenseService} so the client can
     * read one server-side rule instead of re-deriving it. See {@code FormSense.SCENT_TRACK}.
     */
    public static final DeferredHolder<MobEffect, ScentTrackingEffect> SCENT_TRACKING =
            MOB_EFFECTS.register("scent_tracking", ScentTrackingEffect::new);

    /**
     * Wolfsbane: the werewolf keeps their mind, never their shape. Read by
     * {@code WerewolfRules.hasWolfsbane}; see {@code heritage.werewolf}.
     */
    public static final DeferredHolder<MobEffect, WolfsbaneEffect> WOLFSBANE =
            MOB_EFFECTS.register("wolfsbane", WolfsbaneEffect::new);

    private ModEffects() {
    }
}
