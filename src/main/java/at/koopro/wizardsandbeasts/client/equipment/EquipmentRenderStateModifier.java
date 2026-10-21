package at.koopro.wizardsandbeasts.client.equipment;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.EquipmentVisibility;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Applies {@link EquipmentVisibility} to a player's render state — the client half of armour hiding.
 *
 * <p>The render state is a per-frame snapshot built for drawing and thrown away after it. Emptying
 * {@code chestEquipment} there removes the chestplate from <em>this frame's picture</em> — vanilla's
 * {@code HumanoidArmorLayer} and GeckoLib's armour mixin both read that field — while the real stack
 * stays in {@link EquipmentSlot#CHEST}, keeps its attribute modifiers, takes damage and syncs as
 * usual. No equipment is ever moved to make something invisible.
 *
 * <p>Runs during render-state extraction, so it covers the world, the inventory preview and the
 * Character Sheet figure alike (all three go through {@code EntityRenderer#createRenderState}).
 *
 * <p>Two exemptions:
 * <ul>
 *   <li>A glider (elytra) in the chest slot is never hidden by clothing: wings are worn over a robe,
 *       not under it, and {@code WingsLayer} reads the same field.</li>
 *   <li>A concealed wearer (Deathly Hallow) loses everything: all armour, both hands, and — via
 *       {@link #isConcealed} — every W&amp;B worn piece. This replaces the old local-player-only
 *       {@code CloakClientViewRestrictionHandler}; observers additionally receive masked equipment
 *       packets from the server ({@code CloakEffectsHandler}), so their copy holds nothing to hide.</li>
 * </ul>
 */
@NullMarked
public final class EquipmentRenderStateModifier {

    /** The resolved visibility for this frame, for the Curios renderers and the debug overlay. */
    public static final ContextKey<EquipmentVisibility.Result> VISIBILITY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "equipment_visibility"));

    private EquipmentRenderStateModifier() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) AvatarRenderer.class, (entity, renderState) -> {
            if (entity instanceof LivingEntity wearer && renderState instanceof AvatarRenderState state) {
                apply(wearer, state);
            }
        });
    }

    private static void apply(LivingEntity wearer, AvatarRenderState state) {
        EquipmentVisibility.Result visibility = EquipmentVisibility.resolve(wearer);
        if (visibility == EquipmentVisibility.NONE) {
            return;
        }
        state.setRenderData(VISIBILITY, visibility);

        if (visibility.concealed()) {
            state.leftHandItemState.clear();
            state.rightHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
            state.rightHandItemStack = ItemStack.EMPTY;
        }
        if (!visibility.isArmorVisible(EquipmentSlot.HEAD)) {
            state.headEquipment = ItemStack.EMPTY;
        }
        if (!visibility.isArmorVisible(EquipmentSlot.CHEST)
                && (visibility.concealed() || !state.chestEquipment.has(DataComponents.GLIDER))) {
            state.chestEquipment = ItemStack.EMPTY;
        }
        if (!visibility.isArmorVisible(EquipmentSlot.LEGS)) {
            state.legsEquipment = ItemStack.EMPTY;
        }
        if (!visibility.isArmorVisible(EquipmentSlot.FEET)) {
            state.feetEquipment = ItemStack.EMPTY;
        }
    }

    /** Whether this frame's wearer is concealed — every W&amp;B piece skips drawing when so. */
    public static boolean isConcealed(EntityRenderState state) {
        EquipmentVisibility.Result visibility = state.getRenderData(VISIBILITY);
        return visibility != null && visibility.concealed();
    }
}
