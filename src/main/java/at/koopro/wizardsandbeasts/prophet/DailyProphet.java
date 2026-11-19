package at.koopro.wizardsandbeasts.prophet;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The Daily Prophet, written from what actually happened on the server.
 *
 * <p>Stories come in three ways, each a fact the server already knows: a Dark or Unforgivable incident the
 * Ministry filed ({@link #onIncident}, places only, names withheld unless the Ministry has named the caster),
 * a witch or wizard killed ({@link #onDeath}), and an advancement worth announcing ({@link #onAdvancement}).
 * Reading a copy prints the last two days of them, newest first, under the masthead; a quiet day gets one of
 * the paper's usual fillers, picked by the date.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DailyProphet {

    public static final int HEADLINES = 5;
    public static final int FILLERS = 6;
    private static final String KEY = "prophet.wizards_and_beasts.";

    private DailyProphet() {}

    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    /** Called when the Ministry files an incident. Only Dark and Unforgivable magic makes the paper. */
    public static void onIncident(MinecraftServer server, Incident incident) {
        if (incident.legalClass() != LegalClass.DARK && incident.legalClass() != LegalClass.UNFORGIVABLE) {
            return;
        }
        String where = incident.pos().getX() + ", " + incident.pos().getZ();
        String spell = MinistryTrace.spellName(incident.spellId()).getString();
        if (incident.attributed()) {
            at.koopro.wizardsandbeasts.house.HousePointRules.forDarkMagic(server, incident);
            ProphetNews.get(server).report(day(server), KEY + "story.dark_named", incident.casterName(), spell, where);
        } else {
            ProphetNews.get(server).report(day(server), KEY + "story.dark_unknown", spell, where);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) {
            return;
        }
        MinecraftServer server = dead.level().getServer();
        String cause = event.getSource().getLocalizedDeathMessage(dead).getString();
        ProphetNews.get(server).report(day(server), KEY + "story.death", cause);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var display = event.getAdvancement().value().display();
        if (display.isEmpty() || !display.get().shouldAnnounceChat()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ProphetNews.get(server).report(day(server), KEY + "story.achievement", player.getGameProfile().name(),
                display.get().getTitle().getString());
    }

    /** Today's issue, as the lines a reader sees. */
    public static List<Component> issue(MinecraftServer server) {
        long today = day(server);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "masthead").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
        lines.add(Component.translatable(KEY + "dateline", today + 1).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        List<ProphetNews.Story> stories = ProphetNews.get(server).since(today - 1);
        int shown = 0;
        for (ProphetNews.Story story : stories) {
            if (shown++ >= HEADLINES) {
                break;
            }
            MutableComponent line = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY);
            lines.add(line.append(Component.translatable(story.key(), story.args().toArray()).withStyle(ChatFormatting.WHITE)));
        }
        if (at.koopro.wizardsandbeasts.house.HousePoints.get(server).any()) {
            lines.add(at.koopro.wizardsandbeasts.house.HouseService.standings(server).copy().withStyle(ChatFormatting.GRAY));
        }
        if (stories.isEmpty()) {
            int filler = (int) Math.floorMod(today * 31 + 7, FILLERS);
            lines.add(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(KEY + "filler." + filler).withStyle(ChatFormatting.WHITE)));
        }
        return lines;
    }

    public static void read(Player reader) {
        if (reader instanceof ServerPlayer player) {
            for (Component line : issue(player.level().getServer())) {
                player.sendSystemMessage(line);
            }
        }
    }
}
