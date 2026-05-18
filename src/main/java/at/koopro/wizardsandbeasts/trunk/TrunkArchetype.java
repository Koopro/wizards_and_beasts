package at.koopro.wizardsandbeasts.trunk;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

public enum TrunkArchetype implements StringRepresentable {
    SCAMANDER_SANCTUARY("scamander_sanctuary"),
    FIELD_CAMP("field_camp"),
    MINISTRY_STANDARD("ministry_standard"),
    SAFEHOUSE("safehouse"),
    ASTRONOMERS_RETREAT("astronomers_retreat");

    public static final Codec<TrunkArchetype> CODEC =
            StringRepresentable.fromValues(TrunkArchetype::values);

    private final String serializedName;

    TrunkArchetype(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String getTranslationKey() {
        return "trunk.archetype.wizards_and_beasts." + serializedName;
    }

    public TrunkArchetype next() {
        TrunkArchetype[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public List<String> defaultBiomeZones() {
        return switch (this) {
            case SCAMANDER_SANCTUARY -> List.of(
                    "minecraft:forest",        // idx 0: W (grindylow)
                    "minecraft:snowy_plains",  // idx 1: NW (snowy)
                    "minecraft:badlands",      // idx 2: N (thunderbird desert)
                    "minecraft:bamboo_jungle", // idx 3: NE (bamboo)
                    "minecraft:jungle",        // idx 4: E (rainforest)
                    "minecraft:forest",        // idx 5: SE (erumpent)
                    "minecraft:forest",        // idx 6: S (rocky/graphorn)
                    "minecraft:forest");       // idx 7: SW (mooncalf)
            case FIELD_CAMP -> List.of(
                    "minecraft:forest",
                    "minecraft:meadow");
            case MINISTRY_STANDARD -> List.of("minecraft:plains");
            case SAFEHOUSE -> List.of("minecraft:dark_forest");
            case ASTRONOMERS_RETREAT -> List.of(
                    "minecraft:snowy_plains",
                    "minecraft:ice_spikes",
                    "minecraft:frozen_river");
        };
    }

    public BlockState wallBlock() {
        return switch (this) {
            case SCAMANDER_SANCTUARY ->
                    Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
            case FIELD_CAMP ->
                    Blocks.STRIPPED_OAK_LOG.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
            case MINISTRY_STANDARD   -> Blocks.STONE_BRICKS.defaultBlockState();
            case SAFEHOUSE           -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            case ASTRONOMERS_RETREAT -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        };
    }

    public BlockState ceilingBlock() {
        return switch (this) {
            case SCAMANDER_SANCTUARY, FIELD_CAMP -> Blocks.LANTERN.defaultBlockState();
            case MINISTRY_STANDARD   -> Blocks.CANDLE.defaultBlockState();
            case SAFEHOUSE           -> Blocks.SOUL_LANTERN.defaultBlockState();
            case ASTRONOMERS_RETREAT ->
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(BlockStateProperties.FACING, Direction.DOWN);
        };
    }

    public boolean isCircular() {
        return this == FIELD_CAMP || this == ASTRONOMERS_RETREAT;
    }

    public boolean hasUtilityRing() {
        return this == MINISTRY_STANDARD;
    }

    public boolean hasCentralShed() {
        return this == SCAMANDER_SANCTUARY;
    }
}
