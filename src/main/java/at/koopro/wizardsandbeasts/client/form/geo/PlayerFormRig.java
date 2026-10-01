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
 * really contains — {@code merperson} swims rather than walking, {@code obscurus} flies. Asking
 * GeckoLib for a clip a file does not define throws at render time, so a missing movement clip is
 * modelled as {@code null} and the controller falls back to idle rather than guessing a name.
 *
 * @param asset        shared asset name under {@code geckolib/models/entity/},
 *                     {@code geckolib/animations/entity/} and {@code textures/entity/}
 * @param idleClip     the always-available clip
 * @param movementClip clip to play while moving, or null when the rig has none
 * @param attackClip   clip to play while swinging, or null when the rig has none
 * @param hurtClip     clip to play while taking damage, or null when the rig has none
 * @param runClip      clip to play while sprinting, or null to keep the movement clip
 * @param airClip      clip to hold while off the ground, or null to carry on as on the ground
 */
@NullMarked
public record PlayerFormRig(String asset, String idleClip, @Nullable String movementClip,
                            @Nullable String attackClip, @Nullable String hurtClip,
                            @Nullable String runClip, @Nullable String airClip) {

    /** A rig with no sprint or airborne clips, which is every form but the stag. */
    public PlayerFormRig(String asset, String idleClip, @Nullable String movementClip,
                         @Nullable String attackClip, @Nullable String hurtClip) {
        this(asset, idleClip, movementClip, attackClip, hurtClip, null, null);
    }

    private static PlayerFormRig rig(String asset, @Nullable String movement) {
        return rig(asset, movement, null, null);
    }

    private static PlayerFormRig rig(String asset, @Nullable String movement,
                                     @Nullable String attack, @Nullable String hurt) {
        return new PlayerFormRig(asset,
                "animation." + asset + ".idle",
                qualify(asset, movement), qualify(asset, attack), qualify(asset, hurt));
    }

    private static PlayerFormRig rig(String asset, @Nullable String movement, @Nullable String attack,
                                     @Nullable String hurt, @Nullable String run, @Nullable String air) {
        return new PlayerFormRig(asset,
                "animation." + asset + ".idle",
                qualify(asset, movement), qualify(asset, attack), qualify(asset, hurt),
                qualify(asset, run), qualify(asset, air));
    }

    private static @Nullable String qualify(String asset, @Nullable String clip) {
        return clip == null ? null : "animation." + asset + "." + clip;
    }

    /**
     * Form id → rig, for the forms whose art exists.
     *
     * <p>Absent entries are the Animagus forms that borrow real vanilla entity models, which is
     * better than any placeholder rig would be. The Stag Animagus has no vanilla analogue, so it has
     * its own rig ({@code tools/animagus_stag_model.py}), and it is the one form that uses the sprint
     * and airborne clips: a gallop and a leap read as a different animal from a walk. It has no death
     * clip because it never dies as a stag: {@code AnimagusEvents.onDeath} ends the transformation at
     * the moment of death, so the vanilla human death is what plays.
     */
    private static final Map<String, PlayerFormRig> BY_FORM_ID = Map.of(
            "werewolf_wolf", rig("werewolf", "walk", "attack", "hit"),
            "centaur_default", rig("centaur", "walk"),
            "goblin_default", rig("goblin_teller", "walk"),
            "merfolk_water", rig("merperson", "swim"),
            "obscurial_dark", rig("obscurus", "fly"),
            "house_elf_default", rig("house_elf", "walk", "attack", null),
            "veela_harpy", rig("veela_harpy", "walk", "attack", "hit"),
            "animagus_stag", rig("animagus_stag", "walk", null, "hit", "run", "leap"));

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
        return clipFor(moving, false, false);
    }

    /**
     * The clip to play right now, in priority order: hurt, then attack, then movement, then idle.
     *
     * <p>Hurt beats attack because being struck mid-swing should interrupt the swing — that is what
     * a reaction is for. Every step falls through to the next when the rig does not declare that
     * clip, so a form with no attack art simply keeps walking rather than naming a clip its file
     * does not define, which throws inside the render pass.
     */
    public String clipFor(boolean moving, boolean attacking, boolean hurt) {
        return clipFor(moving, false, false, attacking, hurt);
    }

    /**
     * The full choice, in priority order: hurt, attack, airborne, sprint, movement, idle.
     *
     * <p>Off the ground beats the gait, since legs in mid-air are not walking. As above, every step
     * falls through when the rig does not declare the clip, so a rig without the newer clips chooses
     * exactly what it always did.
     */
    public String clipFor(boolean moving, boolean sprinting, boolean airborne, boolean attacking,
                          boolean hurt) {
        if (hurt && hurtClip != null) {
            return hurtClip;
        }
        if (attacking && attackClip != null) {
            return attackClip;
        }
        if (airborne && airClip != null) {
            return airClip;
        }
        if (moving && sprinting && runClip != null) {
            return runClip;
        }
        return moving && movementClip != null ? movementClip : idleClip;
    }
}
