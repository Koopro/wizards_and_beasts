package at.koopro.wizardsandbeasts.command.debug.inspect;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * The fallback: whatever you are looking at, described by what any block can tell you.
 *
 * <p>Registered last, so it only ever answers for something no feature claimed. It exists because
 * "a debug overlay for every feature" is a promise that a hand-written inspector list cannot keep —
 * there will always be a block nobody got to — and a panel that says <em>nothing</em> over an
 * unclaimed block is indistinguishable from a panel that is broken.
 *
 * <p>The block entity's saved tag is printed verbatim. It is ugly and it is the point: a field that
 * exists in the save and nowhere in a hand-written dump is exactly the field that is wrong.
 *
 * <p><b>It prints every block entity, including vanilla ones.</b> That means a chest's contents and a
 * locked container's tag are readable by anyone in debug mode. Toggling debug for yourself is an
 * operator action, so that is the operator reading their own world; {@code /wandb debug toggle all}
 * hands the same reach to every player on the server, and is worth meaning before typing.
 */
@NullMarked
public final class GenericBlockInspector implements DebugInspector.OfBlock {

    /** Characters of one NBT value before it is cut. A long list is noise at panel width. */
    private static final int VALUE_CLIP = 64;

    @Override
    public String id() {
        return "block";
    }

    @Override
    public String summary() {
        return "Any block: id, blockstate, owning module, block-entity tag.";
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir();
    }

    @Override
    public DebugReport inspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer viewer) {
        DebugReport report = DebugReport.of(state.getBlock().getName().getString()
                + " @ " + pos.toShortString());
        report.row("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        Module owner = ModuleContentIndex.moduleOf(state.getBlock());
        report.row("module", owner == null ? "(untagged)" : owner.name());
        if (owner != null) {
            report.flag("module accessible", ModuleContentIndex.isAccessible(state.getBlock()));
        }

        if (!state.getProperties().isEmpty()) {
            report.section("blockstate");
            for (Property<?> property : state.getProperties()) {
                report.row("  " + property.getName(), valueName(state, property));
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            report.note("no block entity");
            return report;
        }
        report.section("block entity");
        report.row("  type", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()) + "");
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        if (tag.isEmpty()) {
            report.row("  tag", "empty");
            return report;
        }
        for (Map.Entry<String, Tag> entry : tag.entrySet()) {
            report.row("  " + entry.getKey(), clip(entry.getValue().toString()));
        }
        return report;
    }

    private static String clip(String value) {
        return value.length() <= VALUE_CLIP ? value : value.substring(0, VALUE_CLIP - 1) + "…";
    }

    /**
     * A property's value as the blockstate spells it.
     *
     * <p>The cast is unavoidable: {@code Property<T>.getName(T)} and {@code BlockState.getValue}
     * agree on {@code T} at runtime but the wildcard loses it, and the alternative is a visitor over
     * every property type for a debug string.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(BlockState state, Property<?> property) {
        return ((Property) property).getName(state.getValue(property));
    }
}
