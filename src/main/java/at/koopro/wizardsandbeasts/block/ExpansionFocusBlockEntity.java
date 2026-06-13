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

import java.util.UUID;

public class ExpansionFocusBlockEntity extends BlockEntity implements MenuProvider {

    private UUID pocketId;

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
}
