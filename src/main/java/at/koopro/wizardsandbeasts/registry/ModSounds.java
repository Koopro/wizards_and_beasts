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

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUND_EVENTS.register(path, () ->
                SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path)));
    }

    private ModSounds() {
    }
}
