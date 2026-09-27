package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;

/**
 * Pixel layout for {@code textures/gui/obscurial/stress_meter.png} (256×36, drawn 1:1).
 * It shipped at 512×72 drawn at half size, two texels per screen pixel beside 1:1 HUD art.
 * Bar fill region matches the flat gray track between the figure and the vortex.
 */
public final class ObscurialStressMeterLayout {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/obscurial/stress_meter.png");

    public static final int TEX_W = 256;
    public static final int TEX_H = 36;

    /** Dark silhouette area: player skin is drawn here (texture pixels). */
    public static final int SKIN_SRC_X = 9;
    public static final int SKIN_SRC_Y = 2;
    public static final int SKIN_SRC_W = 14;
    public static final int SKIN_SRC_H = 32;

    /** Gray stress track (texture pixels). */
    public static final int BAR_SRC_X = 23;
    public static final int BAR_SRC_Y = 9;
    public static final int BAR_SRC_W = 200;
    public static final int BAR_SRC_H = 18;

    /** Drawn HUD width in scaled GUI pixels (height derived from texture aspect). */
    public static final int HUD_DEST_W = 256;

    private ObscurialStressMeterLayout() {}
}
