package at.koopro.wizardsandbeasts.client.form.geo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Which GeckoLib rig a player form draws, and which clips that rig actually has.
 *
 * <p>Every one of these rigs <b>already ships</b>, animated and textured, and was being used only as
 * a mob. The player-form path drew a hand-written {@code ModelPart} duplicate instead — flat
 * hierarchy, every box at {@code texOffs(0, 0)}, no {@code setupAnim}, and a hardcoded tint standing
 * in for a texture that was never authored. This table is the join that was missing.
 *
 * <p><b>Clip names are not assumed.</b> Each rig declares only the clips its {@code .animation.json}
 * really contains — {@code goblin_teller} has an idle and no walk, {@code merperson} swims rather
 * than walking, {@code obscurus} flies. Asking GeckoLib for a clip a file does not define throws at
 * render time, so a missing movement clip is modelled as {@code null} and the controller falls back
 * to idle rather than guessing a name.
 *
 * @param asset        shared asset name under {@code geckolib/models/entity/},
 *                     {@code geckolib/animations/entity/} and {@code textures/entity/}
 * @param idleClip     the always-available clip
 * @param movementClip clip to play while moving, or null when the rig has none
 */
@NullMarked
public record PlayerFormRig(String asset, String idleClip, @Nullable String movementClip) {

    private static PlayerFormRig rig(String asset, @Nullable String movement) {
        return new PlayerFormRig(asset,
                "animation." + asset + ".idle",
                movement == null ? null : "animation." + asset + "." + movement);
    }

    /**
     * Form id → rig, for the forms whose art exists.
     *
     * <p>Absent entries fall through to the legacy hand-written models in {@code FormModelRenderer};
     * house-elf and veela-harpy have no rig authored yet, and the Animagus forms borrow real vanilla
     * entity models, which is better than any placeholder rig would be.
     */
    private static final Map<String, PlayerFormRig> BY_FORM_ID = Map.of(
            "werewolf_wolf", rig("werewolf", "walk"),
            "centaur_default", rig("centaur", "walk"),
            "goblin_default", rig("goblin_teller", null),
            "merfolk_water", rig("merperson", "swim"),
            "obscurial_dark", rig("obscurus", "fly"));

    /** The rig for a form, or null when the form has no GeckoLib art and must use the legacy path. */
    public static @Nullable PlayerFormRig forForm(String formId) {
        return BY_FORM_ID.get(formId);
    }

    public static boolean hasRig(String formId) {
        return BY_FORM_ID.containsKey(formId);
    }

    /** Every form id backed by a rig. Exists so a test can assert the assets on disk match. */
    public static java.util.Set<String> riggedFormIds() {
        return BY_FORM_ID.keySet();
    }

    /**
     * GeckoLib expands this short form to {@code geckolib/models/entity/<asset>.geo.json}, matching
     * how {@code DisguisableBeastGeoModel} and {@code WandRenderer.WandModel} address their assets.
     */
    public Identifier modelResource() {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "entity/" + asset);
    }

    /** Textures are the one path GeckoLib does not expand — it must be written out in full. */
    public Identifier textureResource() {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/entity/" + asset + ".png");
    }

    public Identifier animationResource() {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "entity/" + asset);
    }

    /** The clip to play at this movement speed; idle whenever the rig has no movement clip. */
    public String clipFor(boolean moving) {
        return moving && movementClip != null ? movementClip : idleClip;
    }
}
