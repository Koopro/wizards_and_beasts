package at.koopro.wizardsandbeasts.client.equipment;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.EquipmentType;
import at.koopro.wizardsandbeasts.equipment.curios.CuriosEquipment;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

/**
 * Weasleys' Wizard Wheezes' Headless Hat: worn, the hat and the head under it are not there (Half-Blood Prince). The
 * hat itself has no model; this hides the wearer's head and hat layer.
 *
 * <p>The render state carries whether the drawn head slot holds the hat ({@link #KEY}, set by
 * {@link EquipmentRenderStateModifier}); {@link #applyToModel} runs from {@code PlayerModelMixin} after the pose is
 * set, because only there are the shared model's parts in reach. Nothing else in the mod writes
 * {@code head.visible}, so it is written for every wearer: the model is shared, and the next player keeps a head.
 */
@NullMarked
public final class HeadlessHat {

    public static final ContextKey<Boolean> KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "headless"));

    private HeadlessHat() {}

    /** Whether the hat is drawn on the wearer (worn, or worn as a cosmetic). */
    public static boolean worn(LivingEntity wearer) {
        boolean[] found = {false};
        CuriosEquipment.forEachRendered(wearer, (slotId, index, stack) -> {
            if (slotId.equals(EquipmentType.HEADWEAR.slotId()) && stack.is(CanonItemRegistry.HEADLESS_HAT.get())) {
                found[0] = true;
            }
        });
        return found[0];
    }

    public static void applyToModel(PlayerModel model, AvatarRenderState state) {
        boolean headless = Boolean.TRUE.equals(state.getRenderData(KEY));
        model.head.visible = !headless;
        if (headless) {
            model.hat.visible = false;
        }
    }
}
