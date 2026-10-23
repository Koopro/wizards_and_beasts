package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import at.koopro.wizardsandbeasts.creature.ability.SlayerCurse;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.Poaching;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The way back from a slayer's mark, and what the mark costs in the meantime.
 *
 * <ul>
 *   <li><b>Atonement.</b> A slayer who earns the deep trust (bond {@value #ATONING_BOND}) of a creature of a protected
 *       species has every slayer mark lifted: a thestral or a phoenix that will vouch for you is the one witness a
 *       unicorn listens to. The dark corruption the killing left is not undone -- that has its own road.</li>
 *   <li><b>No gleaning.</b> A slayer cannot pick up what the species they slew left behind (a unicorn's shed hair):
 *       the animal that shuns them does not feed them either.</li>
 * </ul>
 *
 * <p>User decision (2026-10-23): the unicorn's curse was permanent with no way to atone, and a slayer could still
 * gather the hair the unicorns shed.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SlayerAtonement {

    /** The bond a protected creature must reach with a slayer before it vouches for them. */
    public static final int ATONING_BOND = 80;

    private SlayerAtonement() {}

    /** Called when {@code keeper}'s bond with {@code creature} crosses {@link #ATONING_BOND}. */
    public static void onDeepTrust(ServerPlayer keeper, LivingEntity creature) {
        if (!Poaching.isProtected(creature)) {
            return;
        }
        List<String> lifted = new ArrayList<>();
        for (String flag : slayerFlags()) {
            if (PlayerAbilityHelper.hasAbilityFlag(keeper, flag)) {
                PlayerAbilityHelper.removeAbilityFlag(keeper, flag);
                lifted.add(flag);
            }
        }
        if (!lifted.isEmpty()) {
            PlayerFeedback.toast(keeper, NoticeKind.SUCCESS,
                    Component.translatable("creature.wizards_and_beasts.slayer.atoned.title"),
                    Component.translatable("creature.wizards_and_beasts.slayer.atoned.body", creature.getDisplayName()));
        }
    }

    /** Every slayer mark a creature definition can leave. */
    public static List<String> slayerFlags() {
        List<String> flags = new ArrayList<>();
        for (CreatureDefinition definition : CreatureDefinitionRegistry.getAll()) {
            for (CreatureAbility ability : definition.abilities()) {
                if (ability instanceof SlayerCurse curse && !flags.contains(curse.flag())) {
                    flags.add(curse.flag());
                }
            }
        }
        return flags;
    }

    /** The mark a slayer of this species carries, or null when killing it leaves none. */
    public static @org.jspecify.annotations.Nullable String slayerFlagOf(EntityType<?> species) {
        CreatureDefinition definition = CreatureDefinitionRegistry.get(BuiltInRegistries.ENTITY_TYPE.getKey(species));
        if (definition == null) {
            return null;
        }
        for (CreatureAbility ability : definition.abilities()) {
            if (ability instanceof SlayerCurse curse) {
                return curse.flag();
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        EntityType<?> species = CreatureSign.speciesOf(event.getItemEntity());
        String flag = species == null ? null : slayerFlagOf(species);
        if (flag != null && PlayerAbilityHelper.hasAbilityFlag(player, flag)) {
            event.setCanPickup(TriState.FALSE);
        }
    }
}
