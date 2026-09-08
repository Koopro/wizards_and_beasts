package at.koopro.wizardsandbeasts.spell.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Every spell known, and a loadout that is not empty.
 *
 * <p>The loadout is the part worth doing. {@code learn_all} has existed for a while and leaves you
 * with a hundred-odd spells and nothing on the bar, which means the first thing you do after running
 * it is open the wheel and assign slots by hand. Filling the slots is the difference between "the
 * spells are learned" and "I can cast one".
 *
 * <p>Proficiency is left alone. It is the thing most often <em>being</em> tested — gates, power
 * scaling, mastery tiers all read it — and a kit that quietly maxed it would hide exactly the
 * behaviour a developer came to look at. {@code /wandb magic proficiency set_all} is one command
 * away when that is what you want.
 */
@NullMarked
public final class SpellDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "spells";
    }

    @Override
    public String title() {
        return "Spells";
    }

    @Override
    public String summary() {
        return "Learn every spell and fill the loadout slots, so there is something to cast.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        PlayerSpellData data = target.getData(ModAttachments.SPELL_DATA.get());
        int before = data.getKnownSpells().size();
        for (Spell spell : Spells.all()) {
            data.learnSpell(spell.getId());
        }
        int learned = data.getKnownSpells().size() - before;
        if (learned == 0) {
            log.skip("all " + before + " spells already known");
        } else {
            log.changed("spells learned", learned + " (now " + data.getKnownSpells().size() + ")");
        }

        int filled = fillEmptyLoadoutSlots(data);
        if (filled == 0) {
            log.skip("loadout already had a spell in every slot");
        } else {
            log.changed("loadout slots filled", filled);
        }
        PlayerStateSyncService.syncSpells(target);
    }

    @Override
    public void reset(ServerPlayer target, DevLog log) {
        PlayerSpellData data = target.getData(ModAttachments.SPELL_DATA.get());
        data.resetAll();
        PlayerStateSyncService.syncSpells(target);
        log.changed("spell knowledge reset", "loadout, cooldowns and counters cleared");
    }

    /**
     * Puts a spell in every empty slot, leaving occupied ones alone.
     *
     * <p>Not overwriting matters: a developer who has arranged a bar to reproduce something does not
     * want dev setup rearranging it, and the whole point of running this again mid-session is that
     * it is safe to.
     */
    private static int fillEmptyLoadoutSlots(PlayerSpellData data) {
        String[] loadout = data.getLoadout();
        var spells = Spells.all().iterator();
        int filled = 0;
        for (int slot = 0; slot < loadout.length && spells.hasNext(); slot++) {
            if (loadout[slot] != null && !loadout[slot].isEmpty()) {
                continue;
            }
            data.setLoadoutSpell(slot, spells.next().getId());
            filled++;
        }
        return filled;
    }
}
