package at.koopro.wizardsandbeasts.trunk.gui;

import at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity;
import at.koopro.wizardsandbeasts.trunk.PocketBiomes;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;

import java.util.List;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class PocketConfiguratorMenu extends AbstractContainerMenu {

    private final ExpansionFocusBlockEntity configurator;
    private final ContainerLevelAccess access;
    private int radius;
    private int biomeIndex;

    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) { case 0 -> radius; case 1 -> biomeIndex; default -> 0; };
        }
        @Override public void set(int index, int value) {
            if (index == 0) radius = value;
            else if (index == 1) biomeIndex = value;
        }
        @Override public int getCount() { return 2; }
    };

    public PocketConfiguratorMenu(int containerId, Inventory playerInventory, ExpansionFocusBlockEntity be) {
        super(ModMenuTypes.POCKET_CONFIGURATOR.get(), containerId);
        this.configurator = be;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), be.getBlockPos());

        if (playerInventory.player.level() instanceof ServerLevel sl && be.getPocketId() != null) {
            TrunkRegistryData.get(sl).getPocket(be.getPocketId()).ifPresent(r -> {
                this.radius = r.pocketRadius();
                List<String> zones = r.biomeZones().isEmpty()
                        ? r.archetype().defaultBiomeZones() : r.biomeZones();
                this.biomeIndex = PocketBiomes.indexOf(zones.get(0));
            });
        }

        addDataSlots(dataAccess);
    }

    public static PocketConfiguratorMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof ExpansionFocusBlockEntity be)) {
            throw new IllegalStateException("No pocket configurator at " + pos);
        }
        return new PocketConfiguratorMenu(containerId, inv, be);
    }

    public int getRadius() { return dataAccess.get(0); }
    public int getBiomeIndex() { return dataAccess.get(1); }
    public ExpansionFocusBlockEntity getConfigurator() { return configurator; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.POCKET_CONFIGURATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
