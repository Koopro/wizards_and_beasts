package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.module.condition.ModuleEnabledCondition;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saved module state and recipe conditions outlive the {@link Module} enum. A module id the build no longer
 * defines used to fail the whole saved map (resetting every other module to config defaults) and a typo'd
 * condition used to throw out of datapack loading.
 */
class ModuleCodecLeniencyTest {

    @Test
    void anUnknownModuleIdDropsOnlyThatEntry() {
        JsonObject json = new JsonObject();
        json.addProperty(ModuleIds.of(Module.WANDS).toString(), "kept");
        json.addProperty("wizards_and_beasts:a_module_removed_since", "dropped");

        var result = ModuleIds.lenientMap(Codec.STRING).parse(JsonOps.INSTANCE, json);

        assertTrue(result.result().isPresent(), "an unknown id must not fail the whole map: " + result.error());
        assertEquals(Map.of(Module.WANDS, "kept"), result.result().get());
    }

    @Test
    void theLenientMapRoundTrips() {
        Map<Module, String> in = Map.of(Module.WANDS, "a", Module.GRINGOTTS, "b");
        var json = ModuleIds.lenientMap(Codec.STRING).encodeStart(JsonOps.INSTANCE, in).getOrThrow();
        assertEquals(in, ModuleIds.lenientMap(Codec.STRING).parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void aMisspeltConditionModuleIsACodecErrorNotAnException() {
        assertTrue(ModuleEnabledCondition.parse("NO_SUCH_MODULE").error().isPresent());
        assertEquals(Module.WANDS, ModuleEnabledCondition.parse("WANDS").getOrThrow());
    }
}
