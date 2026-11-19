package at.koopro.wizardsandbeasts.spell.revelio;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.Set;

/**
 * Homenum Revelio: tells the caster whether there are people about, walls or no walls (Hermione in Grimmauld
 * Place, <i>Deathly Hallows</i> ch. 9). It names how many and how near the closest is, and outlines nobody;
 * that is what keeps it apart from {@link Revelio}, which shows what is hidden in sight.
 *
 * <p>"People" are other players and the world's people: villagers, illagers, witches, the mod's settlement
 * folk and goblins. Beasts are not people, which is the point of the spell.
 */
@NullMarked
public final class HomenumRevelio {

    public static final double DEFAULT_RADIUS = 24.0;

    private HomenumRevelio() {}

    public static boolean cast(ServerLevel level, ServerPlayer caster, Spell spell) {
        double radius = spell instanceof at.koopro.wizardsandbeasts.spell.core.JsonSpell json
                && json.definition().range() > 0 ? json.definition().range() : DEFAULT_RADIUS;
        Set<LivingEntity> people = people(level, caster, radius);
        if (people.isEmpty()) {
            PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.homenum_revelio.nobody"));
            return true;
        }
        double nearest = people.stream().mapToDouble(p -> p.distanceTo(caster)).min().orElse(radius);
        PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.homenum_revelio.found",
                people.size(), (int) Math.ceil(nearest)));
        return true;
    }

    /** Everyone but the caster within {@code radius}, through walls. */
    public static Set<LivingEntity> people(ServerLevel level, ServerPlayer caster, double radius) {
        AABB box = caster.getBoundingBox().inflate(radius);
        Set<LivingEntity> found = new HashSet<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, HomenumRevelio::isPerson)) {
            if (e != caster && e.isAlive() && e.distanceTo(caster) <= radius) {
                found.add(e);
            }
        }
        // Players listed separately: the section lookup can miss a player placed this tick.
        for (Player p : level.getPlayers(p -> p != caster && !p.isSpectator() && p.distanceTo(caster) <= radius)) {
            found.add(p);
        }
        return found;
    }

    public static boolean isPerson(LivingEntity e) {
        return e instanceof Player || e instanceof AbstractVillager || e instanceof AbstractIllager
                || e instanceof Witch
                || e instanceof at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity
                || e instanceof at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
    }
}
