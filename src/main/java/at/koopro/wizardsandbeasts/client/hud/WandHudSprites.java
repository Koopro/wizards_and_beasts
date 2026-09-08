package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprite ids for the wand HUD, and the one place a spell id becomes an icon.
 *
 * <h2>Why these are sprites</h2>
 * The wand HUD used to name forty standalone {@code .png} files and hand each one to
 * {@code GuiGraphics.blit}, which binds a texture per draw: the plate, the trim, one of four
 * selection rings, and up to four spell icons, every frame, each its own bind. Everything under
 * {@code textures/gui/sprites/} is stitched into vanilla's {@code minecraft:textures/atlas/gui.png}
 * instead, so the same HUD is now one atlas the whole GUI already has bound. It is also the reason
 * the mod's own blood bar and panel chrome already live there — this brings the wand HUD in line
 * with the rest rather than inventing a second atlas beside vanilla's.
 *
 * <h2>Why the icon lookup is cached</h2>
 * Resolving a spell icon has to fall back to a placeholder, because 34 icons exist for 155 spells.
 * The old {@code ModTextures.resolveWandHudSpellIcon} answered that by asking the
 * {@link ResourceManager} whether the file was present — on every call, which meant six resource
 * lookups per frame from {@code SpellDiamondOverlay} alone, plus one per visible row in the spell
 * menu. The answer can only change on a resource reload, so it is cached until one happens.
 *
 * <p>The cache is keyed by spell id and cleared by {@link Reload}, which the client wires to its
 * reload listeners. A resource pack that adds an icon therefore takes effect on {@code F3+T} like
 * any other texture.
 */
@NullMarked
public final class WandHudSprites {

    /** The diamond plate the four slots sit on. */
    public static final Identifier PLATE = sprite("wand_hud/bg");
    /** Gold trim, drawn over the icons and their cooldown sweeps. */
    public static final Identifier TRIM = sprite("wand_hud/overlay");
    /** Shown when a spell has no icon of its own. */
    public static final Identifier SPELL_PLACEHOLDER = sprite("wand_hud/spells/placeholder");

    /** Per-slot selection ring, indexed by slot. */
    private static final Identifier[] SELECTED = {
            sprite("wand_hud/overlay_selected_0"),
            sprite("wand_hud/overlay_selected_1"),
            sprite("wand_hud/overlay_selected_2"),
            sprite("wand_hud/overlay_selected_3"),
    };

    /** Spell id -> resolved icon sprite. Cleared on resource reload; see the class doc. */
    private static final Map<String, Identifier> ICON_CACHE = new ConcurrentHashMap<>();

    private WandHudSprites() {}

    /** The selection ring for {@code slot}, clamped to the four that exist. */
    public static Identifier selectedTrim(int slot) {
        return SELECTED[Math.max(0, Math.min(SELECTED.length - 1, slot))];
    }

    /**
     * The icon for {@code spellId}, or the placeholder when the mod ships none.
     *
     * <p>Safe to call every frame: at most one {@link ResourceManager} lookup per spell id per
     * resource reload.
     */
    public static Identifier spellIcon(String spellId) {
        String path = spellPathSegment(spellId);
        return ICON_CACHE.computeIfAbsent(path, WandHudSprites::resolveIcon);
    }

    /**
     * Drops the resolved-icon cache so a reloaded resource pack's icons are picked up.
     *
     * <p>Registered on the client reload cycle by {@code WizardsAndBeastsClient}. Without it a pack
     * added at runtime would keep drawing the placeholder for every icon that had already been
     * resolved once — the cache would outlive the answer it cached.
     */
    public static final class Reload implements ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            ICON_CACHE.clear();
        }
    }

    private static Identifier resolveIcon(String path) {
        Identifier icon = sprite("wand_hud/spells/" + path);
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        return resources.getResource(texturePath(icon)).isPresent() ? icon : SPELL_PLACEHOLDER;
    }

    /** A GUI sprite id is a bare name; this is the file it is stitched from. */
    private static Identifier texturePath(Identifier sprite) {
        return Identifier.fromNamespaceAndPath(
                sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /** {@code wizards_and_beasts:lumos} and {@code lumos} both name the same icon. */
    private static String spellPathSegment(String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return "unknown";
        }
        int separator = spellId.indexOf(':');
        return separator >= 0 ? spellId.substring(separator + 1) : spellId;
    }
}
