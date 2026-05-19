package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.item.SimpleTooltipItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

final class RegistryUtils {
    private RegistryUtils() {
    }

    static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    static DeferredItem<BroomItem> registerBroom(String name, int durability) {
        return ModItems.ITEMS.registerItem(name,
                props -> new BroomItem(props.stacksTo(1).durability(durability), modId(name)));
    }

    static DeferredItem<SimpleTooltipItem> registerTooltipItem(String name, String tooltipKey, int stackSize) {
        return ModItems.ITEMS.registerItem(name,
                props -> new SimpleTooltipItem(props.stacksTo(stackSize), tooltipKey));
    }

    static DeferredItem<BlockItem> registerSimpleBlockItem(String name, DeferredBlock<? extends Block> block) {
        return ModItems.ITEMS.registerSimpleBlockItem(name, block);
    }

    static BlockBehaviour.Properties unlitTorchProps() {
        return BlockBehaviour.Properties.of()
                .noCollision()
                .instabreak()
                .sound(SoundType.WOOD);
    }

    static BlockBehaviour.Properties lanternProps() {
        return BlockBehaviour.Properties.of()
                .requiresCorrectToolForDrops()
                .strength(3.5F)
                .sound(SoundType.LANTERN);
    }

    static BlockBehaviour.Properties metalCauldronProps() {
        return BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.COPPER).noOcclusion();
    }
}
