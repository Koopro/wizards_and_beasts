package at.koopro.wizardsandbeasts.client.butterbeer;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The world going slightly soft at the edges after a Butterbeer.
 *
 * <p>A gentle widening of the view and nothing else — no wobble, no sway, no screen tint. That
 * restraint is the requirement rather than a shortcut: this is a drink children's characters have in
 * the books, and anything that read as being drunk would make the item unusable on the servers it is
 * most obviously for.
 *
 * <p>Eased in and out on the same {@code ComputeFovModifierEvent} the broom uses, which multiplies
 * the player's own FOV setting rather than replacing it. It also stops writing to the event entirely
 * once it has settled back, so a player with no Mellow left is left with exactly the FOV every other
 * system computed. Honours {@code reduceScreenEffects}, like every other camera effect in the mod.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class MellowFovHandler {

    /**
     * How much wider the view goes at full Mellow.
     *
     * <p>Four percent. Deliberately at the edge of noticing: enough that the world feels a shade more
     * open, far short of anything that could make a player feel unwell.
     */
    private static final float FOV_BONUS = 0.04f;

    /** Per-frame smoothing, so it arrives and leaves rather than snapping. */
    private static final float SMOOTHING = 0.04f;

    private static float smoothed;

    private MellowFovHandler() {}

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean mellow = player != null
                && !Config.reduceScreenEffects
                && player.hasEffect(ModEffects.MELLOW);

        smoothed = Mth.lerp(SMOOTHING, smoothed, mellow ? FOV_BONUS : 0.0f);
        if (smoothed < 0.0005f) {
            smoothed = 0.0f;
            return;
        }
        event.setNewFovModifier(event.getNewFovModifier() * (1.0f + smoothed));
    }
}
