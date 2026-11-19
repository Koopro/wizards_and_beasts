package at.koopro.wizardsandbeasts.item.trinket;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * A Punching Telescope: put it to your eye and it punches you in it (Hermione's black eye, Half-Blood
 * Prince ch. 5). A knock back, a moment's darkness, and a bruise nothing but bruise paste would shift.
 * It is not used up — Fred and George sold it as a prank, to hand to somebody else.
 */
@NullMarked
public class PunchingTelescopeItem extends Item {

    public PunchingTelescopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            player.hurtServer(server, server.damageSources().generic(), 1.0f);
            Vec3 back = player.getLookAngle().scale(-0.6);
            player.push(back.x, 0.15, back.z);
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS,
                    1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("item.wizards_and_beasts.punching_telescope.punched")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            player.getCooldowns().addCooldown(stack, 40);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}
