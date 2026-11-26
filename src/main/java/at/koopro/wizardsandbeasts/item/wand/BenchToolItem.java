package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * One of the wandmaker's bench tools. Right-click the bench with it to lay it on the bench; sneak with an empty hand to
 * take your tools back. On the bench it makes its own stage of a making easier ({@link BenchTool}).
 */
@NullMarked
public class BenchToolItem extends Item {

    private final BenchTool tool;

    public BenchToolItem(Properties properties, BenchTool tool) {
        super(properties.stacksTo(1));
        this.tool = tool;
    }

    public BenchTool tool() {
        return tool;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.wizards_and_beasts.bench_tool.stage." + tool.getSerializedName())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.wizards_and_beasts.bench_tool.hint")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
