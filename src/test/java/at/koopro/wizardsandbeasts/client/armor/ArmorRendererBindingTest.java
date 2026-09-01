package at.koopro.wizardsandbeasts.client.armor;

import org.junit.jupiter.api.Test;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the reflective hop from an armour item to its renderer still lands.
 *
 * <p>{@code WizardArmorItem#rendererClassName} names its renderer as a string, because the item is
 * common code and the renderer is client-only. {@code ClientClassBridge} resolves that string with
 * {@code Class.forName} and {@code getDeclaredConstructor} — both of which fail at <em>render</em>
 * time, inside the armour layer, the first time somebody equips the piece. A renamed or moved
 * renderer class is a compile-clean crash.
 *
 * <p><b>Known gap:</b> the names below are a second copy of the strings in the item classes, since
 * {@code rendererClassName()} is an instance method and an armour item cannot be constructed in a
 * test at all (its {@code repairable} tag needs an unfrozen registry — see
 * {@code WizardArmorMaterialsTest}). So this catches the renderer moving out from under the item; it
 * cannot catch a typo made only on the item side.
 */
class ArmorRendererBindingTest {

    /** Renderer class name → the constructor signature the matching item passes. */
    private static final Map<String, Class<?>[]> BINDINGS = Map.of(
            "at.koopro.wizardsandbeasts.client.armor.StudentRobeRenderer", new Class<?>[0],
            "at.koopro.wizardsandbeasts.client.armor.AurorRobeRenderer", new Class<?>[0],
            "at.koopro.wizardsandbeasts.client.armor.DeathEaterRobeRenderer", new Class<?>[0],
            "at.koopro.wizardsandbeasts.client.armor.WizardHatRenderer", new Class<?>[0],
            "at.koopro.wizardsandbeasts.client.armor.DeathEaterMaskRenderer", new Class<?>[]{int.class});

    @Test
    void everyNamedRendererResolvesToARealClass() {
        BINDINGS.keySet().forEach(className ->
                assertDoesNotThrow(() -> Class.forName(className), className + " is named by an armour item"));
    }

    @Test
    void everyNamedRendererHasTheConstructorItsItemCallsThrough() throws Exception {
        for (Map.Entry<String, Class<?>[]> binding : BINDINGS.entrySet()) {
            Class<?> rendererClass = Class.forName(binding.getKey());
            Constructor<?> constructor = assertDoesNotThrow(
                    () -> rendererClass.getDeclaredConstructor(binding.getValue()),
                    binding.getKey() + " lost the constructor its item passes arguments to");
            assertTrue(constructor != null);
        }
    }

    @Test
    void everyNamedRendererIsActuallyAnArmourRenderer() throws Exception {
        // ClientClassBridge casts the result to GeoArmorRenderer; a renderer that drifted onto some
        // other base would throw ClassCastException at render time rather than here.
        for (String className : BINDINGS.keySet()) {
            assertTrue(GeoArmorRenderer.class.isAssignableFrom(Class.forName(className)),
                    className + " must extend GeoArmorRenderer");
        }
    }
}
