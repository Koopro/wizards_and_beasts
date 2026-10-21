package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.function.Consumer;

/**
 * Base class for the mod's worn GeckoLib wardrobe: real armour <em>or</em> Curios clothing.
 *
 * <p><b>Two ways to be worn.</b> {@link Wear#ARMOR} pieces (robe trousers and boots) run their
 * {@code Properties} through {@code humanoidArmor()} and are vanilla armour in every respect.
 * {@link Wear#CURIO} pieces (the robe body, the wizard hat, the masks) are clothing: no armour
 * components at all, worn in the Curios slot their {@code wearables} data-map entry names, and drawn by
 * {@code client.equipment.curios.WornGeoCurioRenderer} through the very same {@link GeoArmorRenderer}
 * this class binds — so one model and one renderer serve both paths. Whatever a clothing piece grants
 * comes from that data-map entry, not from {@link #material()}.
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
 * referenced directly, the same way {@code AnimatedItem} reaches its renderer: this class is
 * common code and loads on a dedicated server, where the client render classes do not exist.
 */
public abstract class WizardArmorItem extends GeoItemBase {

    /** How a piece is worn. */
    public enum Wear {
        /** A vanilla armour slot, with vanilla armour mechanics. */
        ARMOR,
        /** A Curios slot, as clothing. */
        CURIO
    }

    private final ArmorMaterial material;
    private final ArmorType armorType;
    private final Wear wear;

    protected WizardArmorItem(Properties properties, ArmorMaterial material, ArmorType armorType, Wear wear) {
        super(wear == Wear.ARMOR ? properties.humanoidArmor(material, armorType) : properties.stacksTo(1));
        this.material = material;
        this.armorType = armorType;
        this.wear = wear;
    }

    /**
     * How each piece of a robe set is worn: the body (torso, sleeves, skirt, hood) is the robe and
     * goes in the Curios body slot, so real armour can be worn under it; the trousers and boots are
     * legitimately leg and foot armour and stay so.
     */
    protected static Wear robeWear(ArmorType armorType) {
        return armorType == ArmorType.CHESTPLATE ? Wear.CURIO : Wear.ARMOR;
    }

    /**
     * The body part this piece's model covers — which GeckoLib bone group it draws. For an armour
     * piece that is also its slot; for clothing it only picks bones (a robe body draws the
     * chestplate group). Read by {@link HoodedRobe} to tell the robe body from its trousers and boots.
     */
    public ArmorType armorType() {
        return this.armorType;
    }

    /** The material this piece was built from. For clothing, informational only. */
    public ArmorMaterial material() {
        return this.material;
    }

    public Wear wear() {
        return this.wear;
    }

    /** Right-click wears clothing in its Curios slot; armour keeps vanilla's equip-swap. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (wear == Wear.CURIO) {
            return EquipmentAPI.equipFromHand(player, hand, material.equipSound());
        }
        return super.use(level, player, hand);
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
