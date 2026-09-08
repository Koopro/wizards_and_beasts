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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
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

    /**
     * How long a study takes.
     *
     * <p>Three seconds. The tome grants <em>permanent</em> KNOWLEDGE credit and History of Magic
     * progress the first time it is opened, and it used to grant all of that on a single click — the
     * most lasting reward in the item layer for the least input in it. Reading is now something a
     * player commits to, and can be interrupted out of.
     */
    public static final int READ_TICKS = 60;

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Started on both sides: the client needs the use to begin for the reading pose, the server
        // to time the study. CONSUME rather than SUCCESS so the arm does not swing as the book opens.
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return READ_TICKS;
    }

    /**
     * {@link ItemUseAnimation#NONE} — the two-handed read belongs to {@code ItemUsePosePass}.
     *
     * <p>Vanilla has no book pose at all. Its five animations raise the item to the mouth, hold it
     * still, or draw it back, and none of them is a person reading.
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * The study lands only if the player read to the end.
     *
     * <p>{@code releaseUsing} is deliberately not overridden, so letting go early is a real cancel
     * rather than a slower click and the tome is left exactly as it was found. That is the point of
     * making this a channel at all.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
            return stack;
        }

        PlayerSkillData skillData = serverPlayer.getData(ModAttachments.SKILL_DATA.get());
        Component title = getName(stack);

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
        return stack;
    }
}
