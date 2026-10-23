package at.koopro.wizardsandbeasts.item.spell;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A book, page or set of notes with one spell written in it. Reading it, then tracing the spell's
 * gesture ({@link SpellTraceService}), teaches that spell.
 *
 * <p>This replaces the spell teacher. The teacher was a lectern that opened a catalogue of every
 * spell the player was eligible for and sold them one for Knuts — a shop, and a shop is the one
 * thing a wizarding education is not. Nothing about <em>who may learn what</em> changes:
 * {@link SpellLearningService} and its eligibility rules are untouched and still own every gate.
 * What changed is the trigger. You have to find the writing first.
 *
 * <h2>Reading is a channel, not a click</h2>
 * {@link #READ_TICKS} of held right-click, like {@code BroomPolishItem}'s rub. A spell learned on a
 * single tap in the middle of a fight would make the source a combat item; three seconds of standing
 * still makes it study. Eligibility is checked twice — once to open the channel so a refusal is
 * immediate and legible, and again in {@link #finishUsingItem}, because the player has had those
 * three seconds to have the spell granted by a skill node, lose a profession, or swap the book.
 *
 * <h2>Bound or loose</h2>
 * A <b>bound</b> source survives being read: a textbook someone has read is still a textbook, and on
 * a server the copy that taught one wizard Lumos has to be lendable to the next. A <b>loose</b> one
 * — a torn page, a sheet of somebody's notes — is spent, because a single leaf that has been read
 * aloud once and then studied to pieces is the only thing a found scrap can plausibly be, and it
 * gives the mid-game sources a reason to keep turning up in loot.
 *
 * <p>Which of the two an item is belongs to the item, not the component. The component answers
 * "which spell", and every stack of a given item answers "is this reusable" the same way; putting
 * the second question in the component would make two visually identical pages behave differently
 * with nothing on the tooltip to say so.
 */
@NullMarked
public class SpellSourceItem extends Item {

    /** Long enough to read as study rather than a click. */
    public static final int READ_TICKS = 60;

    private final boolean spentOnRead;

    /** A bound source: survives being read. */
    public SpellSourceItem(Properties properties) {
        this(properties, false);
    }

    public SpellSourceItem(Properties properties, boolean spentOnRead) {
        super(properties);
        this.spentOnRead = spentOnRead;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Blankness is read off the component rather than by resolving the spell: an id whose
        // definition a pack has since dropped is a book that is written but unreadable, which is not
        // the same refusal as a blank one. Resolving the actual Spell is the server branch's job.
        if (SpellSource.isBlank(stack)) {
            refuse(player, level, Component.translatable("item.wizards_and_beasts.spell_source.blank"));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            Spell spell = SpellSource.spellOf(stack);
            SpellLearningService.LearnResult check = SpellLearningService.validateLearnAttempt(serverPlayer, spell);
            if (!check.success()) {
                refuse(player, level, check.text());
                return InteractionResult.FAIL;
            }
        }
        // CONSUME rather than SUCCESS: SUCCESS swings the arm, and a swing at the start of a read
        // reads as the reading having already happened.
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return READ_TICKS;
    }

    /** {@link ItemUseAnimation#NONE}: holding a book open is {@code ItemUsePosePass}'s job, not one of vanilla's five. */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * The end of the reading channel opens the tracing challenge; it does not teach. The spell is written
     * into the player only by {@link SpellTraceService} after a passed trace, and only then is a loose page
     * spent — a page refused by a gate, or traced badly, has not been studied to pieces.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return stack;
        }
        InteractionHand hand = player.getUsedItemHand();
        if (SpellSource.spellOf(stack) != null) {
            SpellTraceService.openFromBook(player, hand);
        }
        return stack;
    }

    /** Whether a passed trace uses this source up: a loose page, as opposed to a bound book. */
    public boolean spentOnRead() {
        return spentOnRead;
    }

    /**
     * Names the stack after what is written in it, so a shelf of books is readable without hovering
     * each one. A blank keeps its registered name.
     *
     * <p>Goes through {@link SpellSource#writtenName}, which builds the name from the id rather than
     * from the registry, so a book whose spell a pack has since removed still says what it was for
     * instead of going untitled.
     */
    @Override
    public Component getName(ItemStack stack) {
        String spellId = SpellSource.spellIdOf(stack);
        if (spellId == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.wizards_and_beasts.spell_source.named",
                super.getName(stack), SpellSource.writtenName(spellId));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        if (SpellSource.isBlank(stack)) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.spell_source.blank.tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltipAdder.accept(Component.translatable(spentOnRead
                        ? "item.wizards_and_beasts.spell_source.tooltip.spent"
                        : "item.wizards_and_beasts.spell_source.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Told on the action bar rather than refused silently, so a wasted click explains itself. */
    private static void refuse(Player player, Level level, @Nullable Component reason) {
        if (!level.isClientSide() && reason != null) {
            PlayerFeedback.actionBar(player, reason.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    /** The Standard Book of Spells: a source that is also drawn as a book in hand. */
    public static class Animated extends SpellSourceItem implements AnimatedItem {
        public Animated(Properties properties) {
            super(properties);
        }
    }
}
