package at.koopro.wizardsandbeasts.item.lore;

import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A studyable lore tome. The first time a player reads a given tome it grants permanent credit
 * toward the KNOWLEDGE stat and the History of Magic OWL — tracked per unique lore id so re-reading
 * the same book yields nothing further (see {@link PlayerSkillData#markLoreEntryRead}). The book is
 * a reusable reference and is never consumed; reading it is a server-authoritative side effect.
 */
public class LoreTomeItem extends Item {

    public LoreTomeItem(Properties properties) {
        super(properties);
    }

    /** The unique lore-source id for this tome: its registry path (e.g. {@code a_history_of_magic}). */
    private String loreId() {
        Identifier key = BuiltInRegistries.ITEM.getKey(this);
        return key.getPath();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.lore_tome.tooltip")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            // Drive the use animation on the client; all state changes happen server-side.
            return InteractionResult.SUCCESS;
        }

        PlayerSkillData skillData = serverPlayer.getData(ModAttachments.SKILL_DATA.get());
        Component title = getName(player.getItemInHand(hand));

        if (skillData.markLoreEntryRead(loreId())) {
            // First study: KNOWLEDGE derives from lore read, so push a fresh stats sync.
            PlayerStatsSyncPayload.syncToPlayer(serverPlayer);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.wizards_and_beasts.lore_tome.studied", title)
                            .withStyle(ChatFormatting.GREEN), true);
        } else {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.wizards_and_beasts.lore_tome.already_studied", title)
                            .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }
}
