package at.koopro.wizardsandbeasts.spell.learning.book;

import at.koopro.wizardsandbeasts.network.spell.SpellBookOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceResultS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceDifficulty;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading a textbook: the server half of the book screen.
 *
 * <p>Holding a book open for the read channel ({@code SpellSourceItem#READ_TICKS}) calls {@link #open}: every
 * chapter is judged against the reader's learning gates and sent as {@link SpellBookOpenS2CPayload}, and every
 * chapter counts as freshly read, which is what lets a chapter pick (or the trace's Try again) open a trace from the
 * book without reading it a second time. Picking a chapter calls {@link #studyChapter}, which opens the spell's
 * trace; only a passed trace teaches ({@link SpellTraceService}). A textbook is never spent.
 */
@NullMarked
public final class SpellBookService {

    private SpellBookService() {
    }

    /** Reads the book in {@code hand} through and shows its chapters. Does nothing for a page or a blank. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String bookId = SpellSource.bookIdOf(stack);
        if (bookId == null) {
            return;
        }
        if (SpellBooks.get(bookId) == null) {
            PlayerFeedback.actionBar(player, Component.translatable("spell.wizards_and_beasts.book.unreadable"));
            return;
        }
        List<SpellBookOpenS2CPayload.Chapter> chapters = chapters(player, stack);
        for (SpellBookOpenS2CPayload.Chapter chapter : chapters) {
            SpellTraceService.noteRead(player, chapter.spellId());
        }
        PacketDistributor.sendToPlayer(player, new SpellBookOpenS2CPayload(bookId, hand.ordinal(), chapters));
    }

    /** The book's chapters as this reader stands with them. Spells a pack has dropped are left out. */
    public static List<SpellBookOpenS2CPayload.Chapter> chapters(ServerPlayer player, ItemStack stack) {
        var known = player.getData(ModAttachments.SPELL_DATA.get());
        List<SpellBookOpenS2CPayload.Chapter> out = new ArrayList<>();
        for (String id : SpellSource.chaptersOf(stack)) {
            Spell spell = Spells.byId(id);
            if (spell == null) {
                continue;
            }
            int difficulty = TraceDifficulty.forSpell(spell).ordinal();
            if (known.knowsSpell(spell.getId())) {
                out.add(new SpellBookOpenS2CPayload.Chapter(spell.getId(), SpellBookOpenS2CPayload.KNOWN, difficulty, ""));
                continue;
            }
            SpellLearningService.LearnResult check = SpellLearningService.validateLearnAttempt(player, spell,
                    SpellLearningService.Route.BOOK);
            out.add(check.success()
                    ? new SpellBookOpenS2CPayload.Chapter(spell.getId(), SpellBookOpenS2CPayload.OPEN, difficulty, "")
                    : new SpellBookOpenS2CPayload.Chapter(spell.getId(), SpellBookOpenS2CPayload.LOCKED, difficulty,
                            SpellTraceResultS2CPayload.encodeDetail(check.text())));
        }
        return out;
    }

    /**
     * The reader picked {@code spellId}: if the book in {@code hand} still teaches it and was read just now, open
     * its trace. Every refusal is told on the action bar.
     */
    public static void studyChapter(ServerPlayer player, InteractionHand hand, String spellId) {
        Spell spell = Spells.byId(spellId);
        ItemStack stack = player.getItemInHand(hand);
        if (spell == null || SpellSource.bookIdOf(stack) == null || !SpellSource.teaches(stack, spell.getId())) {
            PlayerFeedback.actionBar(player, Component.translatable("spell.wizards_and_beasts.trace.no_source"));
            return;
        }
        if (!SpellTraceService.isReadFresh(player, spell.getId())) {
            PlayerFeedback.actionBar(player, Component.translatable("spell.wizards_and_beasts.trace.read_first"));
            return;
        }
        SpellTraceService.openChapter(player, hand, spell);
    }
}
