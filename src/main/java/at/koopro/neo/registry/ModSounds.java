package at.koopro.neo.registry;

import at.koopro.neo.Neo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Neo.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BROOM_CRASH =
            SOUND_EVENTS.register("broom_crash", () ->
                    SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(Neo.MODID, "broom_crash")));

    private ModSounds() {
    }
}
