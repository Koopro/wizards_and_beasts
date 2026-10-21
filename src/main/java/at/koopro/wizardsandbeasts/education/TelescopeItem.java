package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Astronomy student's telescope (Hogwarts first-year list). Held up to a clear night sky for three seconds, it
 * charts the moon's phase and the night's highest constellation ({@link Astronomy}) — one observation a night, the
 * coursework of the Astronomy O.W.L. and N.E.W.T. The sky has to be there to be read: night, open above, and a world
 * with a sky at all, the same conditions a centaur's star reading keeps.
 */
@NullMarked
public class TelescopeItem extends Item {

    /** Three seconds at the eyepiece; letting go early charts nothing. */
    public static final int OBSERVE_TICKS = 60;
    private static final String KEY = "education.telescope.";

    public TelescopeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return OBSERVE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
            observe(serverLevel, player);
        }
        return stack;
    }

    /** Why the sky cannot be read here and now, or {@code null} when it can. */
    public static @Nullable String refusal(ServerLevel level, ServerPlayer player) {
        var dimension = level.dimensionType();
        if (!dimension.hasSkyLight() || dimension.hasFixedTime()) return "no_sky";
        if (!WerewolfRules.isNight(level.getDayTime())) return "daylight";
        if (!level.canSeeSky(player.blockPosition().above())) return "roofed";
        if (level.isRaining()) return "clouded";
        return null;
    }

    /** Charts tonight's sky for {@code player}, or says why not. Returns whether anything new was charted. */
    public static boolean observe(ServerLevel level, ServerPlayer player) {
        String why = refusal(level, player);
        if (why != null) {
            player.displayClientMessage(Component.translatable(KEY + why).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        long dayTime = level.getDayTime();
        long night = Astronomy.night(dayTime);
        int phase = WerewolfRules.moonPhase(dayTime);
        Astronomy.Constellation constellation = Astronomy.constellationOn(night);
        Component seen = Component.translatable(KEY + "seen", Component.translatable(constellation.nameKey()),
                Component.translatable(Astronomy.phaseKey(phase)));

        PlayerEducationData data = player.getData(ModAttachments.EDUCATION_DATA.get());
        if (data.lastObservedNight() == night) {
            player.displayClientMessage(Component.translatable(KEY + "already", seen).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int before = Astronomy.points(data.moonPhases(), data.constellations());
        PlayerEducationData next = data.withObservation(Astronomy.phaseBit(phase), constellation.bit(), night);
        player.setData(ModAttachments.EDUCATION_DATA.get(), next);
        int after = Astronomy.points(next.moonPhases(), next.constellations());
        player.sendSystemMessage(seen.copy().withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  ").append(Component.translatable(constellation.factKey()))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        player.displayClientMessage(Component.translatable(KEY + "charted", after, Astronomy.maxPoints())
                .withStyle(after > before ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        EducationService.sync(player);
        return after > before;
    }
}
