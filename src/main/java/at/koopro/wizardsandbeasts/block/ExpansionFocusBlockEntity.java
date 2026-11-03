package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.trunk.gui.PocketConfiguratorMenu;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The pocket configurator: the focus of an Undetectable Extension Charm. Drawn as an armillary by its GeckoLib rig
 * ({@code tools/station_rigs.py}): rings turning slowly about a floating crystal, spun up when it is used.
 */
public class ExpansionFocusBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {

    /** Opened: the rings whirl up and the crystal lifts. */
    public static final String CLIP_ATTUNE = "attune";
    private static final String CONTROLLER = "focus";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pocket_configurator.idle");

    private UUID pocketId;
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public ExpansionFocusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POCKET_CONFIGURATOR.get(), pos, state);
    }

    public UUID getPocketId() {
        return pocketId;
    }

    public void setPocketId(UUID id) {
        this.pocketId = id;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (pocketId != null) {
            output.store("pocketId", UUIDUtil.CODEC, pocketId);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pocketId = input.read("pocketId", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PocketConfiguratorMenu(containerId, playerInventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.wizards_and_beasts.pocket_configurator");
    }

    /** Plays a one-shot clip for everyone watching; call on the server. */
    public void play(String clip) {
        triggerAnim(CONTROLLER, clip);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ExpansionFocusBlockEntity>(CONTROLLER, 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim(CLIP_ATTUNE, RawAnimation.begin().thenPlay("animation.pocket_configurator.attune")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }
}
