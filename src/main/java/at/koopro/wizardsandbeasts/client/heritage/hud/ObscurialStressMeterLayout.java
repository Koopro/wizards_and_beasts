package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;

/**
 * Pixel layout for {@code textures/gui/obscurial/stress_meter.png} (512×72).
 * Bar fill region matches the flat gray track between the figure and the vortex.
 */
public final class ObscurialStressMeterLayout {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/obscurial/stress_meter.png");

    public static final int TEX_W = 512;
    public static final int TEX_H = 72;

    /** Dark silhouette area: player skin is drawn here (texture pixels). */
    public static final int SKIN_SRC_X = 18;
    public static final int SKIN_SRC_Y = 4;
    public static final int SKIN_SRC_W = 28;
    public static final int SKIN_SRC_H = 64;

    /** Gray stress track (texture pixels). */
    public static final int BAR_SRC_X = 46;
    public static final int BAR_SRC_Y = 18;
    public static final int BAR_SRC_W = 399;
    public static final int BAR_SRC_H = 36;

    /** Drawn HUD width in scaled GUI pixels (height derived from texture aspect). */
    public static final int HUD_DEST_W = 256;

    private ObscurialStressMeterLayout() {}
}
