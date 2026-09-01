package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.function.Consumer;

/**
 * Base class for the mod's worn armour.
 *
 * <p>There is no {@code ArmorItem} to extend in 1.21.11 — {@code Properties#humanoidArmor} is the
 * whole of what the old class did, applied as data components. This type exists so a piece still
 * *reports* the material and slot it was built from: the creative tab, tooltips and the GeckoLib
 * layer all want to ask an item what it is without unpacking its components.
 *
 * <p>Extends {@link GeoItemBase} because {@link GeoArmorRenderer} is declared
 * {@code <T extends Item & GeoItem, ...>} — a piece of armour that is not a {@code GeoItem} cannot
 * be rendered by GeckoLib at all, no matter how the renderer is registered.
 *
 * <p><b>Binding the renderer.</b> There is no registration event for armour. GeckoLib mixes into
 * {@code HumanoidArmorLayer} and, for every armour piece about to be drawn, asks the item itself for
 * a renderer via {@code GeoRenderProvider.of(stack).getGeoArmorRenderer(stack, slot)}. So the bind
 * happens here, in {@link #createGeoRenderer}, and nothing needs adding to {@code ClientSetup}.
 * Returning {@code null} from that provider is the supported way to fall back to the vanilla layer.
 *
 * <p>The renderer is named as a string and built through {@link ClientClassBridge} rather than
 * referenced directly, the same way {@code CoinItem} reaches {@code CoinRenderer}: this class is
 * common code and loads on a dedicated server, where the client render classes do not exist.
 */
public abstract class WizardArmorItem extends GeoItemBase {

    private final ArmorMaterial material;
    private final ArmorType armorType;

    protected WizardArmorItem(Properties properties, ArmorMaterial material, ArmorType armorType) {
        super(properties.humanoidArmor(material, armorType));
        this.material = material;
        this.armorType = armorType;
    }

    // -- GeckoLib ------------------------------------------------------------------------------

    /**
     * Fully-qualified name of the {@link GeoArmorRenderer} subclass that draws this piece.
     *
     * <p>A string rather than a {@code Class} on purpose — see the class javadoc. Named per subclass
     * rather than derived from the item id because a whole set shares one renderer: the chest, legs
     * and boots of a robe are three items drawn from one model, and GeckoLib picks which bones each
     * one contributes from its {@link ArmorType}.
     */
    protected abstract String rendererClassName();

    /** Constructor signature of {@link #rendererClassName()}. Empty for a set with no variants. */
    protected Class<?>[] rendererArgTypes() {
        return new Class<?>[0];
    }

    /** Arguments matching {@link #rendererArgTypes()}. */
    protected Object[] rendererArgs() {
        return new Object[0];
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?, ?> renderer;

            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(
                    net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
                if (this.renderer == null)
                    this.renderer = ClientClassBridge.instantiate(
                            rendererClassName(),
                            GeoArmorRenderer.class,
                            rendererArgTypes(),
                            rendererArgs());
                return this.renderer;
            }
        });
    }

    /**
     * A registered but idle controller.
     *
     * <p>{@code SingletonGeoAnimatable} requires one, and armour still has to be an animatable to be
     * rendered at all. Nothing in the wardrobe animates yet, so this holds the bind point open —
     * mirrors {@code CoinItem}, which is a static model on the same footing.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WizardArmorItem>(
                "armor_controller", 0,
                state -> PlayState.STOP));
    }
}
