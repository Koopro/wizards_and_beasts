package at.koopro.wizardsandbeasts.prophet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

/**
 * A newspaper you read with a use: the Daily Prophet prints today's issue ({@link DailyProphet}); the Quibbler
 * prints three of Xenophilius Lovegood's headlines, picked by the date, and is never about anything that
 * happened.
 */
@NullMarked
public class NewspaperItem extends Item {

    public static final int QUIBBLER_STORIES = 8;

    private final boolean quibbler;

    public NewspaperItem(Properties properties, boolean quibbler) {
        super(properties);
        this.quibbler = quibbler;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer reader) {
            level.playSound(null, reader.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            if (quibbler) {
                long day = DailyProphet.day(reader.level().getServer());
                reader.sendSystemMessage(Component.translatable("prophet.wizards_and_beasts.quibbler.masthead")
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE));
                for (int i = 0; i < 3; i++) {
                    int story = (int) Math.floorMod(day * 3 + i, QUIBBLER_STORIES);
                    reader.sendSystemMessage(Component.literal("\u2022 ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("prophet.wizards_and_beasts.quibbler." + story)));
                }
            } else {
                DailyProphet.read(reader);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
