package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;

public final class ModTextures {
    public static final Identifier WAND_HUD_BG =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/wand_hud/wand_hud_bg.png");
    public static final Identifier WAND_HUD_OVERLAY =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/wand_hud/wand_hud_overlay.png");
    public static final Identifier WAND_HUD_SPELL_PLACEHOLDER =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/wand_hud/spells/placeholder.png");
    public static final Identifier GENERIC_ICON_PLACEHOLDER =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/type_icons/placeholder.png");

    private ModTextures() {
    }

    public static Identifier wandHudSpellIcon(String spellId) {
        String path = spellPathSegment(spellId);
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/wand_hud/spells/" + path + ".png");
    }

    public static Identifier wandHudSelectedOverlay(int slot) {
        int safeSlot = Math.max(0, Math.min(3, slot));
        return Identifier.fromNamespaceAndPath(
                WizardsAndBeastsMod.MODID,
                "textures/gui/wand_hud/wand_hud_overlay_selected_" + safeSlot + ".png");
    }

    /**
     * Resolves the spell icon if present; otherwise returns a placeholder texture.
     */
    public static Identifier resolveWandHudSpellIcon(ResourceManager resourceManager, String spellId) {
        Identifier icon = wandHudSpellIcon(spellId);
        if (resourceManager.getResource(icon).isPresent()) {
            return icon;
        }
        if (resourceManager.getResource(WAND_HUD_SPELL_PLACEHOLDER).isPresent()) {
            return WAND_HUD_SPELL_PLACEHOLDER;
        }
        return GENERIC_ICON_PLACEHOLDER;
    }

    public static Identifier resolveWandHudSelectedOverlay(ResourceManager resourceManager, int slot) {
        Identifier overlay = wandHudSelectedOverlay(slot);
        if (resourceManager.getResource(overlay).isPresent()) {
            return overlay;
        }
        return WAND_HUD_OVERLAY;
    }

    private static String spellPathSegment(String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return "unknown";
        }
        int separator = spellId.indexOf(':');
        return separator >= 0 ? spellId.substring(separator + 1) : spellId;
    }
}
