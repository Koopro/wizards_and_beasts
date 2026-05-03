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
            SOUND_EVENTS.register("broom_crash", () ->
                    SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom_crash")));

    private ModSounds() {
    }
}
