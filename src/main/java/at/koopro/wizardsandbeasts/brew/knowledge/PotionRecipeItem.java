package at.koopro.wizardsandbeasts.brew.knowledge;

import at.koopro.wizardsandbeasts.brew.BrewNaming;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Something you read to learn brewing recipes: a potions textbook (what it teaches is data,
 * {@code brew_recipe_sources/<item id>.json}) or a loose recipe page (the recipe is written on the stack).
 *
 * <p>Reading takes three seconds and teaches whatever in it you did not know; the book or page is kept. It is your
 * reference: its tooltip spells out the ingredients, the pot and the catalyst of every recipe in it that you know.
 */
@NullMarked
public class PotionRecipeItem extends Item {

    public static final int READ_TICKS = 60;
    private static final String KEY = "item.wizards_and_beasts.potion_recipes.";

    public PotionRecipeItem(Properties properties) {
        super(properties);
    }

    /** The recipes in this stack: written on a page, or what this textbook teaches. */
    public static List<String> recipesIn(ItemStack stack, boolean server) {
        List<String> written = stack.get(ModDataComponents.BREW_RECIPES.get());
        if (written != null) {
            return written;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return server ? RecipeSources.recipes(Identifier.fromNamespaceAndPath(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(), path)) : KnownRecipesView.book(path);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && recipesIn(stack, true).isEmpty()) {
            PlayerFeedback.actionBar(player, Component.translatable(KEY + "blank").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return READ_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return stack;
        }
        List<String> learned = BrewKnowledge.learn(player, recipesIn(stack, true));
        if (learned.isEmpty()) {
            PlayerFeedback.actionBar(player, Component.translatable(KEY + "nothing_new").withStyle(ChatFormatting.GRAY));
            return stack;
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < learned.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(BrewKnowledge.name(learned.get(i)));
        }
        PlayerFeedback.toast(player, NoticeKind.SUCCESS, Component.translatable(KEY + "learned.title"),
                Component.translatable(KEY + "learned.body", names));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        List<String> recipes = recipesIn(stack, false);
        if (recipes.isEmpty()) {
            return;
        }
        tooltip.accept(Component.translatable(KEY + "teaches").withStyle(ChatFormatting.GRAY));
        for (String recipeId : recipes) {
            BrewKnowledgeS2CPayload.Line line = KnownRecipesView.line(recipeId);
            Component name = line == null ? Component.literal(recipeId)
                    : Component.translatable(BrewNaming.nameKey(line.output()));
            boolean known = KnownRecipesView.knows(recipeId);
            tooltip.accept(Component.literal(" - ").append(name)
                    .withStyle(known ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            if (known && line != null) {
                describe(line, tooltip);
            }
        }
        tooltip.accept(Component.translatable(KEY + "hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** "2 Dittany, 3 Bone Meal, 1 Ghast Tear in a brass cauldron", then the catalyst, if any. */
    private static void describe(BrewKnowledgeS2CPayload.Line line, Consumer<Component> tooltip) {
        MutableComponent ingredients = Component.empty();
        for (int i = 0; i < line.ingredients().size(); i++) {
            String[] parts = line.ingredients().get(i).split("\\*");
            if (i > 0) {
                ingredients.append(", ");
            }
            ingredients.append(parts.length > 1 ? parts[1] + " " : "").append(itemName(parts[0]));
        }
        tooltip.accept(Component.literal("   ").append(Component.translatable(KEY + "makes", ingredients,
                Component.translatable(KEY + "tier." + line.tier().toLowerCase(Locale.ROOT)))).withStyle(ChatFormatting.GRAY));
        if (!line.catalyst().isEmpty()) {
            String[] at = line.catalyst().split("@");
            String[] window = at.length > 1 ? at[1].split("-") : new String[0];
            int from = window.length > 1 ? Math.round(Float.parseFloat(window[0]) * 100) : 0;
            int to = window.length > 1 ? Math.round(Float.parseFloat(window[1]) * 100) : 0;
            tooltip.accept(Component.literal("   ").append(Component.translatable(KEY + "catalyst", itemName(at[0]), from, to))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component itemName(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed) ? Component.literal(id)
                : BuiltInRegistries.ITEM.getValue(parsed).getName(new ItemStack(BuiltInRegistries.ITEM.getValue(parsed)));
    }
}
