package at.koopro.wizardsandbeasts.event.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Consumers for the Goblin "Guild Ledger" skill-tree ability flags
 * (granted via {@link at.koopro.wizardsandbeasts.skill.SkillEffect.UnlockAbility}):
 * <ul>
 *   <li>{@code goblin_appraisal} — sneak-use a wizarding coin to read your total coin worth.</li>
 *   <li>{@code goblin_steelheart} — goblin-steel resolve halves fall and explosion damage.</li>
 *   <li>{@code goblin_ledger} — items you drop are contract-bound: they never despawn.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class GoblinAbilityHandler {

    private GoblinAbilityHandler() {}

    /** Appraisal: sneak + right-click a wizarding coin → action-bar total coin worth. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;

        ItemStack held = event.getItemStack();
        if (!isWizardingCoin(held)) return;
        if (!SkillSystemAPI.hasAbility(player, "goblin_appraisal")) return;

        Inventory inv = player.getInventory();
        long galleons = CurrencyHelper.countItem(inv, CurrencyItemRegistry.GALLEON.get());
        long sickles = CurrencyHelper.countItem(inv, CurrencyItemRegistry.SICKLE.get());
        long knuts = CurrencyHelper.countItem(inv, CurrencyItemRegistry.KNUT.get());
        long total = CurrencyHelper.toKnuts(galleons, sickles, knuts);

        player.displayClientMessage(Component.literal("Appraised worth: " + CurrencyHelper.formatFromKnuts(total))
                .withStyle(ChatFormatting.GOLD), true);
        event.setCanceled(true);
    }

    /** Steelheart: halve fall and explosion damage. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypeTags.IS_FALL) && !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;
        if (!SkillSystemAPI.hasAbility(player, "goblin_steelheart")) return;
        event.setAmount(event.getAmount() * 0.5f);
    }

    /** Ledger: a goblin's dropped goods are contract-bound and never despawn. */
    @SubscribeEvent
    public static void onItemJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        if (!(item.getOwner() instanceof ServerPlayer player)) return;
        if (SkillSystemAPI.hasAbility(player, "goblin_ledger")) {
            item.setUnlimitedLifetime();
        }
    }

    private static boolean isWizardingCoin(ItemStack stack) {
        return stack.is(CurrencyItemRegistry.GALLEON.get())
                || stack.is(CurrencyItemRegistry.SICKLE.get())
                || stack.is(CurrencyItemRegistry.KNUT.get());
    }
}
