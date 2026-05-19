package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;

/**
 * Skill tree node sprites under {@code assets/wizards_and_beasts/textures/gui/skill_tree/}.
 * <p>
 * Incomplete multi-rank nodes: {@code skill.png} base, then overlay {@code skill_{N}_uncompleted.png}
 * where {@code N == maxLevel} ({@code N >= 2}). Optional {@code skill_hover.png} and {@code skill_selected.png}
 * on top (transparent PNG outside the ring so the base circle shows through).
 * <p>
 * Partial progress ({@code 1 <= level < maxLevel}, {@code maxLevel >= 2}): after {@code skill_{N}_uncompleted.png},
 * a <strong>left strip</strong> of {@code skill_{N}_completed.png} is drawn with width
 * {@code (NODE_TEXELS * level) / maxLevel} (same fraction on-screen). Lay out filled segments left-to-right in the
 * completed sheet so the strip matches your bar count (cuts differ for N=2 vs 3 vs 4 by using this fraction, not a fixed half).
 * <p>
 * Maxed nodes: full {@code skill_completed.png}, then for {@code maxLevel >= 2} overlay {@code skill_{N}_completed.png}
 * (filled bars on transparent). One-rank maxed uses only {@code skill_completed.png}.
 */
public final class SkillTreeGuiTextures {

    /**
     * Pixel size of skill_tree PNG sheets (width = height). Must match your textures (e.g. 64×64); used for UV sampling when blitting.
     */
    public static final int NODE_TEXELS = 64;
    public static final Identifier SKILL_NODE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/skill.png");
    public static final Identifier SKILL_NODE_COMPLETED =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/skill_completed.png");
    public static final Identifier SKILL_NODE_SELECTED =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/skill_selected.png");
    /** Overlay when the mouse is over an incomplete node (under {@link #SKILL_NODE_SELECTED}). */
    public static final Identifier SKILL_NODE_HOVER =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/skill_hover.png");

    private SkillTreeGuiTextures() {}

    /** {@code textures/gui/skill_tree/skill_{N}_uncompleted.png} for {@code maxLevel >= 2}. */
    public static Identifier rankUncompletedOverlay(int maxLevel) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/gui/skill_tree/skill_" + maxLevel + "_uncompleted.png");
    }

    /** {@code skill_{maxLevel}_completed.png} overlay (bars); use only when {@code maxLevel >= 2}. */
    public static Identifier rankCompletedOverlay(int maxLevel) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/gui/skill_tree/skill_" + maxLevel + "_completed.png");
    }
}
