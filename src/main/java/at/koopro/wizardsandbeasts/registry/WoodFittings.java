package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * What the wand woods were missing as building wood, and the doors of the location families (World Decoration
 * Renaissance, W2b). Each wand wood gets its own {@link WoodType} and {@link BlockSetType}, so its signs carry its
 * own board texture and its doors their own sounds, and the full set vanilla woods have: door, trapdoor, fence,
 * fence gate, button, pressure plate, sign and hanging sign. Signs are vanilla's blocks (editable text, the sign
 * block entities, which {@link #addToSignTypes} extends to them).
 *
 * <p>Textures are written by {@code tools/wood_fittings_textures.py}; models, loot, tags and names are datagen's.
 */
public final class WoodFittings {

    private WoodFittings() {}

    /** One wand wood's fittings. */
    public record Fittings(WoodSet wood, WoodType type, DeferredBlock<DoorBlock> door, DeferredBlock<TrapDoorBlock> trapdoor,
                           DeferredBlock<FenceBlock> fence, DeferredBlock<FenceGateBlock> gate,
                           DeferredBlock<ButtonBlock> button, DeferredBlock<PressurePlateBlock> plate,
                           DeferredBlock<StandingSignBlock> sign, DeferredBlock<WallSignBlock> wallSign,
                           DeferredBlock<CeilingHangingSignBlock> hangingSign,
                           DeferredBlock<WallHangingSignBlock> wallHangingSign,
                           DeferredItem<SignItem> signItem, DeferredItem<HangingSignItem> hangingSignItem,
                           String displayName) {}

    /** A door with its trapdoor, for a location family. */
    public record DoorPair(String id, String name, DeferredBlock<DoorBlock> door, DeferredBlock<TrapDoorBlock> trapdoor,
                           boolean metal) {}

    public static final List<Fittings> WOODS = new ArrayList<>();
    public static final List<DoorPair> DOORS = new ArrayList<>();
    /** Every item here, in creative-tab order. */
    public static final List<Supplier<? extends Item>> TAB = new ArrayList<>();

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava();
    }

    private static <T extends Block> DeferredBlock<T> block(String id, java.util.function.Function<BlockBehaviour.Properties, T> factory,
                                                             Supplier<BlockBehaviour.Properties> props, boolean item) {
        DeferredBlock<T> block = ModBlocks.BLOCKS.registerBlock(id, factory, props);
        if (item) {
            TAB.add(ModItems.ITEMS.registerSimpleBlockItem(id, block));
        }
        return block;
    }

    private static Fittings fittings(WoodSet wood) {
        String name = wood.name();
        String display = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        BlockSetType set = BlockSetType.register(new BlockSetType(WizardsAndBeastsMod.MODID + ":" + name));
        WoodType type = WoodType.register(new WoodType(WizardsAndBeastsMod.MODID + ":" + name, set));
        MapColor color = MapColor.WOOD;
        DeferredBlock<DoorBlock> door = ModBlocks.BLOCKS.registerBlock(name + "_door", p -> new DoorBlock(set, p),
                () -> wood(color).strength(3.0f).noOcclusion().pushReaction(PushReaction.DESTROY));
        TAB.add(ModItems.ITEMS.registerItem(name + "_door", p -> new DoubleHighBlockItem(door.get(), p),
                p -> p.useBlockDescriptionPrefix()));
        DeferredBlock<TrapDoorBlock> trapdoor = block(name + "_trapdoor", p -> new TrapDoorBlock(set, p),
                () -> wood(color).strength(3.0f).noOcclusion().isValidSpawn((s, l, pos, e) -> false), true);
        DeferredBlock<FenceBlock> fence = block(name + "_fence", FenceBlock::new, () -> wood(color), true);
        DeferredBlock<FenceGateBlock> gate = block(name + "_fence_gate", p -> new FenceGateBlock(type, p),
                () -> wood(color), true);
        DeferredBlock<ButtonBlock> button = block(name + "_button", p -> new ButtonBlock(set, 30, p),
                () -> BlockBehaviour.Properties.of().noCollision().strength(0.5f).pushReaction(PushReaction.DESTROY), true);
        DeferredBlock<PressurePlateBlock> plate = block(name + "_pressure_plate", p -> new PressurePlateBlock(set, p),
                () -> wood(color).noCollision().strength(0.5f).pushReaction(PushReaction.DESTROY), true);
        Supplier<BlockBehaviour.Properties> signProps = () -> wood(color).noCollision().strength(1.0f);
        DeferredBlock<StandingSignBlock> sign = block(name + "_sign", p -> new StandingSignBlock(type, p), signProps, false);
        DeferredBlock<WallSignBlock> wallSign = block(name + "_wall_sign", p -> new WallSignBlock(type, p), signProps, false);
        DeferredBlock<CeilingHangingSignBlock> hanging = block(name + "_hanging_sign",
                p -> new CeilingHangingSignBlock(type, p), signProps, false);
        DeferredBlock<WallHangingSignBlock> wallHanging = block(name + "_wall_hanging_sign",
                p -> new WallHangingSignBlock(type, p), signProps, false);
        DeferredItem<SignItem> signItem = ModItems.ITEMS.registerItem(name + "_sign",
                p -> new SignItem(sign.get(), wallSign.get(), p), p -> p.stacksTo(16).useBlockDescriptionPrefix());
        DeferredItem<HangingSignItem> hangingItem = ModItems.ITEMS.registerItem(name + "_hanging_sign",
                p -> new HangingSignItem(hanging.get(), wallHanging.get(), p), p -> p.stacksTo(16).useBlockDescriptionPrefix());
        TAB.add(signItem);
        TAB.add(hangingItem);
        return new Fittings(wood, type, door, trapdoor, fence, gate, button, plate, sign, wallSign, hanging, wallHanging,
                signItem, hangingItem, display);
    }

    private static void doors(String id, String name, BlockSetType set, MapColor color, boolean metal) {
        Supplier<BlockBehaviour.Properties> props = () -> BlockBehaviour.Properties.of().mapColor(color)
                .strength(metal ? 5.0f : 3.0f).sound(metal ? SoundType.COPPER : SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.DESTROY);
        DeferredBlock<DoorBlock> door = ModBlocks.BLOCKS.registerBlock(id + "_door", p -> new DoorBlock(set, p), props);
        TAB.add(ModItems.ITEMS.registerItem(id + "_door", p -> new DoubleHighBlockItem(door.get(), p),
                p -> p.useBlockDescriptionPrefix()));
        DeferredBlock<TrapDoorBlock> trapdoor = block(id + "_trapdoor", p -> new TrapDoorBlock(set, p),
                () -> props.get().isValidSpawn((s, l, pos, e) -> false), true);
        DOORS.add(new DoorPair(id, name, door, trapdoor, metal));
    }

    static {
        for (WoodSet wood : ModBlocks.ALL_WOOD_SETS) {
            WOODS.add(fittings(wood));
        }
        doors("hogwarts_oak", "Hogwarts Oak", BlockSetType.SPRUCE, MapColor.COLOR_BROWN, false);
        doors("ministry_gilt", "Ministry Gilt", BlockSetType.DARK_OAK, MapColor.COLOR_BLACK, false);
        doors("gringotts_bronze", "Gringotts Bronze", BlockSetType.COPPER, MapColor.COLOR_ORANGE, true);
        doors("diagon_green", "Diagon Green Shop", BlockSetType.JUNGLE, MapColor.COLOR_GREEN, false);
        doors("diagon_purple", "Diagon Purple Shop", BlockSetType.JUNGLE, MapColor.COLOR_PURPLE, false);
        doors("knockturn_iron", "Knockturn Iron", BlockSetType.COPPER, MapColor.COLOR_BLACK, true);
    }

    /** Vanilla's sign block entities accept only vanilla's sign blocks; ours are added (mod bus). */
    public static void addToSignTypes(BlockEntityTypeAddBlocksEvent event) {
        for (Fittings f : WOODS) {
            event.modify(BlockEntityType.SIGN, f.sign().get(), f.wallSign().get());
            event.modify(BlockEntityType.HANGING_SIGN, f.hangingSign().get(), f.wallHangingSign().get());
        }
    }

    public static void init() { /* force class load, triggering static field registration */ }
}
