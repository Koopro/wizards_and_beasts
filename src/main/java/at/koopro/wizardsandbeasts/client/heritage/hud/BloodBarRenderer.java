package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientBloodState;
import at.koopro.wizardsandbeasts.heritage.vampire.ThirstStage;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The row of blood drops that stands where the hunger bar used to be.
 *
 * <p><b>This is {@code Gui.renderFood} with different sprites.</b> Ten 9×9 icons laid right to left on
 * an 8-pixel pitch, a socket blitted for every one of them and a filled drop over the ones the pool
 * covers, with a half sprite for the odd value — the same loop, the same arithmetic, the same
 * {@code blitSprite(RenderPipelines.GUI_TEXTURED, …)} call. Deliberately so: a bar that is drawn a
 * different way from vanilla's bars looks like a different mod's HUD sitting next to the hearts, however
 * carefully its colours are chosen.
 *
 * <p><b>Geometry is vanilla's too.</b> The row occupies exactly the rectangle the drumsticks did — right
 * edge at {@code guiWidth/2 + 91}, at the current {@code rightHeight} — and then advances
 * {@code rightHeight} by 10 exactly as {@code Gui.renderFoodLevel} would have. Without that last step the
 * air-bubble row, which stacks on the same counter, rides up into this bar the moment a vampire goes
 * underwater.
 *
 * <p>Registered above {@code VanillaGuiLayers.FOOD_LEVEL} so it lands in the same place in the draw order
 * as the layer it stands in for; the vanilla layer itself is cancelled by {@link NutritionHudHandler}.
 */
@NullMarked
public final class BloodBarRenderer {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "blood_bar");

    /** Right edge of the vanilla status rows, relative to the screen centre. */
    private static final int RIGHT_EDGE_OFFSET = 91;

    /** Icons in the row, and the half-units each represents — ten drops, twenty halves. */
    private static final int ICONS = 10;
    private static final int UNITS = ICONS * 2;

    /** Icon size and pitch. Nine wide, overlapping by one, exactly as vanilla lays out food. */
    private static final int ICON = 9;
    private static final int PITCH = 8;

    /** What {@code Gui.renderFoodLevel} adds to {@code rightHeight} once it has drawn. */
    private static final int ROW_ADVANCE = 10;

    /**
     * The shake. Vanilla jitters food icons by a pixel when saturation runs out; this does the same when
     * the pool does, which is the established HUD vocabulary for "this is about to start hurting" and
     * costs no art. Cadence is tied to how much is left, so it quickens as the bar empties.
     */
    private static final RandomSource JITTER = RandomSource.create();

    private BloodBarRenderer() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // The same conditions vanilla puts its own survival rows behind. A creative vampire has no hunger
        // bar to replace, and drawing a blood meter over a hidden HUD is worse than drawing nothing.
        if (mc.player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        ThirstStage stage = ClientBloodState.stage();
        @Nullable NutritionBarTheme theme = NutritionBarTheme.of(ClientBloodState.policy(), stage);
        if (theme == null) {
            return;
        }
        // Vanilla suppresses the food row while riding something with health of its own, and puts the
        // mount's hearts there instead. Matching that keeps the two from overlapping.
        if (isRidingHealthBearer(mc.player)) {
            return;
        }

        int right = graphics.guiWidth() / 2 + RIGHT_EDGE_OFFSET;
        int top = graphics.guiHeight() - mc.gui.rightHeight;
        int level = Mth.clamp(Math.round(ClientBloodState.percent() * UNITS), 0, UNITS);
        boolean shaking = stage == ThirstStage.STARVING
                && mc.player.tickCount % (level * 3 + 1) == 0;

        for (int icon = 0; icon < ICONS; icon++) {
            int x = right - icon * PITCH - ICON;
            int y = shaking ? top + JITTER.nextInt(3) - 1 : top;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, theme.empty(), x, y, ICON, ICON);
            if (icon * 2 + 1 < level) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, theme.full(), x, y, ICON, ICON);
            } else if (icon * 2 + 1 == level) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, theme.half(), x, y, ICON, ICON);
            }
        }

        if (Config.enableDebugTools) {
            // Debug-only, and a number rather than an icon: the point of it is to read an exact value
            // while tuning drain and feed rates, which is precisely what ten drops are bad at.
            String readout = Component.translatable("gui.wizards_and_beasts.blood.readout",
                    Math.round(ClientBloodState.blood()), Math.round(ClientBloodState.maxBlood())).getString();
            int left = right - (ICONS - 1) * PITCH - ICON;
            graphics.drawString(mc.font, readout, left - mc.font.width(readout) - 4, top + 1, 0xFFE8C4CC, true);
        }

        mc.gui.rightHeight += ROW_ADVANCE;
    }

    /** True while vanilla would be drawing a mount's health where the food row goes. */
    private static boolean isRidingHealthBearer(Player player) {
        return player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity mount
                && mount.getMaxHealth() > 0f;
    }
}
