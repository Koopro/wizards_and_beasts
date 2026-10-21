package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Icons from data, drawn safely.
 *
 * <p>Abilities, spells and datapack content name their icon as a texture path, and a path nobody
 * painted draws the magenta-and-black checkerboard -- which is what the ability wheel showed for every
 * ability it had. An icon whose texture does not exist is drawn instead as a monogram: the first letter
 * of the thing's name, gilt on the night socket. It reads as unfinished art, not as a broken game.
 *
 * <p>Icon scale: icons are drawn at their native 16px (or 32px for a large socket), never at a fraction --
 * pixel art at 1.5x shimmers. Existence is cached and cleared with the resource packs.
 */
@NullMarked
public final class UiIcons {

    private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();

    private UiIcons() {}

    public static boolean exists(Identifier texture) {
        return EXISTS.computeIfAbsent(texture,
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
    }

    /** Draws a 16px icon at (x, y), or the monogram of {@code name} when the texture is missing. */
    public static void icon16(GuiGraphics g, Font font, Identifier texture, Component name, int x, int y) {
        if (exists(texture)) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
            return;
        }
        String text = name.getString().strip();
        String letter = text.isEmpty() ? "?" : text.substring(0, text.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        g.fill(x + 1, y + 1, x + 15, y + 15, 0x40000000 | (WizardsPalette.GILT & 0xFFFFFF));
        g.fill(x + 1, y + 1, x + 15, y + 2, WizardsPalette.GILT_DARK);
        g.fill(x + 1, y + 14, x + 15, y + 15, WizardsPalette.GILT_DARK);
        g.fill(x + 1, y + 2, x + 2, y + 14, WizardsPalette.GILT_DARK);
        g.fill(x + 14, y + 2, x + 15, y + 14, WizardsPalette.GILT_DARK);
        g.drawString(font, letter, x + 8 - font.width(letter) / 2, y + 4, WizardsPalette.GILT_LIGHT, true);
    }

    /** Clears the cache when resource packs change. */
    public static final class Reload implements ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            EXISTS.clear();
        }
    }
}
