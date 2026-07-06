package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_CRASH =
            register("broom_crash");

    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CAST_GENERIC =
            register("spell_cast_generic");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CAST_DARK =
            register("spell_cast_dark");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CAST_CHARM =
            register("spell_cast_charm");

    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_GENERIC =
            register("spell_impact_generic");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_EXPELLIARMUS =
            register("spell_impact_expelliarmus");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_STUPEFY =
            register("spell_impact_stupefy");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_IMPACT_AVADA =
            register("spell_impact_avada");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_FIZZLE =
            register("spell_fizzle");

    public static final DeferredHolder<SoundEvent, SoundEvent> WAND_EQUIP =
            register("wand_equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAND_UNEQUIP =
            register("wand_unequip");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAND_SWING =
            register("wand_swing");

    public static final DeferredHolder<SoundEvent, SoundEvent> PATRONUS_SUMMON =
            register("patronus_summon");

    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_RAISE =
            register("protego_raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_BLOCK =
            register("protego_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_SHATTER =
            register("protego_shatter"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_HORRIBILIS_ABSORB =
            register("protego_horribilis_absorb"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_TOTALUM_RAISE =
            register("protego_totalum_raise"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> PROTEGO_MAXIMA_RAISE =
            register("protego_maxima_raise"); // vanilla-event remap in sounds.json; bespoke asset optional
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CLASH =
            register("spell_clash");

    // Niffler
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_AMBIENT =
            register("entity.niffler.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_HURT =
            register("entity.niffler.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_DEATH =
            register("entity.niffler.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_EAT =
            register("entity.niffler.eat");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_HAPPY =
            register("entity.niffler.happy");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_HISS =
            register("entity.niffler.hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_DIG =
            register("entity.niffler.dig");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIFFLER_SQUIRM =
            register("entity.niffler.squirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> BABY_NIFFLER_AMBIENT =
            register("entity.baby_niffler.ambient");

    // Deluminator
    public static final DeferredHolder<SoundEvent, SoundEvent> DELUMINATOR_ABSORB = register("deluminator_absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> DELUMINATOR_RESTORE = register("deluminator_restore");

    // Two-Way Mirror
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_CONNECT = register("mirror_connect");

    // Horcrux
    public static final DeferredHolder<SoundEvent, SoundEvent> HORCRUX_SHATTER = register("horcrux_shatter");

    // Resurrection Stone
    public static final DeferredHolder<SoundEvent, SoundEvent> RESURRECTION_STONE_TURN = register("resurrection_stone_turn");

    // Blood Pact
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_OFFER = register("blood_pact_offer");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_SEAL = register("blood_pact_seal");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_SHATTER = register("blood_pact_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_PACT_BLOCK = register("blood_pact_block");

    // Floo Network
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_WHOOSH = register("floo_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_IGNITE = register("floo_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOO_LAND = register("floo_land");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUND_EVENTS.register(path, () ->
                SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path)));
    }

    private ModSounds() {
    }
}
