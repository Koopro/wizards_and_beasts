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
    public static final DeferredHolder<MobEffect, MuffliatoEffect> MUFFLIATO =
            MOB_EFFECTS.register("muffliato", MuffliatoEffect::new);
    public static final DeferredHolder<MobEffect, CruciatusPainEffect> CRUCIATUS_PAIN =
            MOB_EFFECTS.register("cruciatus_pain", CruciatusPainEffect::new);
    public static final DeferredHolder<MobEffect, DementorChillEffect> DEMENTOR_CHILL =
            MOB_EFFECTS.register("dementor_chill", DementorChillEffect::new);
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

    public static final DeferredHolder<MobEffect, DisorientedEffect> DISORIENTED =
            MOB_EFFECTS.register("disoriented", DisorientedEffect::new);

    public static final DeferredHolder<MobEffect, SoulDrainedEffect> SOUL_DRAINED =
            MOB_EFFECTS.register("soul_drained", SoulDrainedEffect::new);

    public static final DeferredHolder<MobEffect, BasiliskGazeLockEffect> BASILISK_GAZE_LOCK =
            MOB_EFFECTS.register("basilisk_gaze_lock", BasiliskGazeLockEffect::new);

    private ModEffects() {
    }
}
