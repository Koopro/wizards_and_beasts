package at.koopro.wizardsandbeasts.client.petrify;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.petrify.state.ClientPetrifyState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * All petrification-rendering logic. A basilisk-gazed player is drawn as a stone statue by
 * <em>substituting the skin on the render state</em> rather than drawing a second body over the
 * first: {@link AvatarRenderer#getTextureLocation} reads {@code state.skin.body().texturePath()},
 * so swapping {@code state.skin} makes vanilla render the ordinary player model in stone. That
 * costs no render layer, no mixin, keeps the entity shadow, and cannot z-fight with the real skin
 * the way an overlaid opaque mesh does.
 *
 * <p>The petrified flag reaches the model through {@link EntityRenderState#setRenderData}, never by
 * reading the entity at render time — {@link #applyFrozenPose} only ever sees a render state.
 * {@code EntityRenderer.createRenderState} allocates a fresh state every frame, so a state that
 * carries no {@link #PETRIFIED} entry is simply a player who is not petrified; nothing has to be
 * cleared afterwards.
 *
 * <p>The stone appearance is a deliberate placeholder — a flat grey skin — see
 * {@code AUDIT_PUNCHLIST.md}, "Petrification appearance".
 */
@NullMarked
public final class PetrifyRenderHandler {

    /** Set on the render state of every petrified player; absent means "not petrified". */
    public static final ContextKey<Boolean> PETRIFIED =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "petrified"));

    private static final ClientAsset.ResourceTexture STONE_BODY = new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "entity/petrify/stone_statue"));

    /**
     * One stone skin per arm width. Cached because the modifier runs for every tracked player every
     * frame, and {@link PlayerSkin} is a record whose identity vanilla compares nowhere — but
     * allocating two objects per player per frame for no reason is still waste.
     */
    private static final Map<PlayerModelType, PlayerSkin> STONE_SKINS = new EnumMap<>(PlayerModelType.class);

    static {
        for (PlayerModelType model : PlayerModelType.values()) {
            // No cape and no elytra: a statue does not wear cloth.
            STONE_SKINS.put(model, PlayerSkin.insecure(STONE_BODY, null, null, model));
        }
    }

    private PetrifyRenderHandler() {}

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        // Raw-typed like the other modifiers in this tree: AvatarRenderer's own type parameter is
        // not expressible through the event's bounds without a TypeToken literal, and the runtime
        // dispatch is on the raw class either way.
        event.registerEntityModifier(
                (Class) AvatarRenderer.class,
                (entity, renderState) -> {
                    if (entity instanceof Player player && renderState instanceof AvatarRenderState state) {
                        modify(player, state);
                    }
                });
    }

    /**
     * Runs after all vanilla data is extracted, on the client only. Reads the synced
     * {@link ClientPetrifyState} cache — the same UUID set every tracking client is sent — so a
     * petrified player turns to stone for everyone in the room, not only for themselves.
     */
    private static void modify(Player player, AvatarRenderState state) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (!ClientPetrifyState.isPetrified(player.getUUID())) {
            return;
        }
        state.setRenderData(PETRIFIED, Boolean.TRUE);
        state.skin = STONE_SKINS.getOrDefault(state.skin.model(), STONE_SKINS.get(PlayerModelType.WIDE));
        // The second skin layer would show the player's own hat/jacket silhouette through the stone.
        state.showHat = false;
        state.showJacket = false;
        state.showCape = false;
        state.showLeftSleeve = false;
        state.showRightSleeve = false;
        state.showLeftPants = false;
        state.showRightPants = false;
    }

    public static boolean isPetrified(@Nullable EntityRenderState state) {
        return state != null && Boolean.TRUE.equals(state.getRenderData(PETRIFIED));
    }

    /**
     * Pins the model into a neutral standing pose. Called from {@code PlayerModelMixin} at the tail
     * of {@code setupAnim}, so it overwrites every rotation vanilla just computed — walk sway, arm
     * swing, the passenger crouch — leaving a statue that does not animate. A no-op for players who
     * are not petrified, which is every player on a normal server.
     */
    public static void applyFrozenPose(PlayerModel model, AvatarRenderState state) {
        if (!isPetrified(state)) {
            return;
        }
        HumanoidModel<?> humanoid = model;
        resetRotation(humanoid.head);
        resetRotation(humanoid.hat);
        resetRotation(humanoid.body);
        resetRotation(humanoid.leftLeg);
        resetRotation(humanoid.rightLeg);
        resetRotation(humanoid.leftArm);
        resetRotation(humanoid.rightArm);
        // A hair of outward splay so the arms read as separate from the torso rather than fusing
        // into one grey slab at the silhouette.
        humanoid.leftArm.zRot = -0.08F;
        humanoid.rightArm.zRot = 0.08F;
    }

    private static void resetRotation(net.minecraft.client.model.geom.ModelPart part) {
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }
}
